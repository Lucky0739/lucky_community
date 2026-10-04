from __future__ import annotations

import unittest
from unittest.mock import patch

from app.agents.extraction import ExtractionOutcome
from app.agents.recheck import RecheckOutcome
from app.governance import govern
from app.moderation.llm import AssessOutcome
from app.retrieval.vector_store import _rrf, embed
from app.schemas import (
    ContentType,
    Evidence,
    ModerateRequest,
    PostExtraction,
    RetrievalResult,
    RiskAssessment,
    RiskCategory,
    RiskLevel,
)


def _extraction(content_type: ContentType = ContentType.SHARE) -> ExtractionOutcome:
    return ExtractionOutcome(
        extraction=PostExtraction(
            title="测试",
            summary="摘要",
            entities=[],
            topics=["测试"],
            content_type=content_type,
            language="zh",
        ),
        degraded=False,
        reason=None,
        model_version="extract-test",
        latency_ms=1,
        token_cost=10,
    )


def _assessment(
    level: RiskLevel = RiskLevel.NONE,
    confidence: float = 0.9,
    categories: list[RiskCategory] | None = None,
) -> AssessOutcome:
    evidence = (
        [Evidence(field="content", quote="加微信", offset=0)]
        if level is not RiskLevel.NONE
        else []
    )
    return AssessOutcome(
        assessment=RiskAssessment(
            risk_level=level,
            categories=categories or [],
            confidence=confidence,
            evidence=evidence,
            reasoning="测试结论",
        ),
        degraded=False,
        degrade_reason=None,
        model_version="review-test",
        latency_ms=1,
        token_cost=20,
    )


class GovernanceGraphTests(unittest.TestCase):
    def test_clean_flow_joins_three_branches(self) -> None:
        request = ModerateRequest(
            post_id=1, bar_id=10, title="测试", content="普通分享"
        )
        with (
            patch("app.governance.extraction.extract", return_value=_extraction()),
            patch("app.governance.vector_store.search", return_value=RetrievalResult()),
            patch("app.governance.moderation.assess", return_value=_assessment()) as review,
            patch("app.governance.vector_store.upsert_post") as upsert,
        ):
            result = govern(request)
        review.assert_called_once()
        upsert.assert_called_once_with(1, 10, "测试", "普通分享")
        self.assertEqual(result.summary, "摘要")
        self.assertFalse(result.has_conflict)
        self.assertEqual(result.retry_count, 0)
        self.assertEqual(result.token_cost, 30)

    def test_conflict_runs_recheck_once(self) -> None:
        request = ModerateRequest(
            post_id=2, bar_id=10, title="求助", content="加微信帮我看看"
        )
        risky = _assessment(RiskLevel.LOW, 0.5, [RiskCategory.AD])
        revised = _assessment(RiskLevel.NONE, 0.8)
        with (
            patch(
                "app.governance.extraction.extract",
                return_value=_extraction(ContentType.HELP),
            ),
            patch("app.governance.vector_store.search", return_value=RetrievalResult()),
            patch(
                "app.governance.moderation.assess",
                return_value=risky,
            ) as review,
            patch(
                "app.governance.recheck_agent.recheck",
                return_value=RecheckOutcome(
                    assessment=revised.assessment,
                    reason=None,
                    model_version="recheck-test",
                    token_cost=15,
                    latency_ms=1,
                ),
            ) as recheck,
            patch("app.governance.vector_store.upsert_post"),
        ):
            result = govern(request)
        review.assert_called_once()
        recheck.assert_called_once()
        self.assertTrue(result.has_conflict)
        self.assertEqual(result.retry_count, 1)
        self.assertEqual(result.assessment.risk_level, RiskLevel.NONE)


class VectorHelpersTests(unittest.TestCase):
    def test_hash_embedding_is_stable_and_normalized(self) -> None:
        first = embed("同一段文本")
        second = embed("同一段文本")
        self.assertEqual(first, second)
        self.assertAlmostEqual(sum(value * value for value in first), 1.0, places=6)

    def test_rrf_merges_duplicate_posts(self) -> None:
        vector_rows = [(1, "A", "a", "未处置", 0.1), (2, "B", "b", "警告", 0.2)]
        keyword_rows = [(2, "B", "b", "警告", 1.0), (3, "C", "c", "未处置", 0.8)]
        result = _rrf(vector_rows, keyword_rows, 3)
        self.assertEqual(result[0].post_id, 2)
        self.assertEqual({item.post_id for item in result}, {1, 2, 3})


if __name__ == "__main__":
    unittest.main()
