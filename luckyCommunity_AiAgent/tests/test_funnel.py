from __future__ import annotations

import unittest
from unittest.mock import patch

from app.moderation import keywords
from app.moderation.llm import AssessOutcome, ScreenOutcome
from app.schemas import Evidence, RiskAssessment, RiskCategory, RiskLevel


class KeywordLayerTests(unittest.TestCase):
    def test_hit_wechat_with_spaces(self) -> None:
        result = keywords.assess("求职", "有课，加 微 信 日入三千")
        self.assertIsNotNone(result)
        assert result is not None
        self.assertEqual(result.risk_level, RiskLevel.LOW)
        self.assertEqual(result.categories, [RiskCategory.AD])
        self.assertEqual(result.confidence, 0.95)
        self.assertEqual(result.evidence[0].quote, "加 微 信")
        self.assertGreaterEqual(result.evidence[0].offset, 0)

    def test_miss_normal_post(self) -> None:
        self.assertIsNone(keywords.assess("今日份猫片", "楼下橘猫又来讨饭了"))


class FunnelTests(unittest.TestCase):
    def test_fingerprint_hit_skips_llm(self) -> None:
        from app.agents import moderation as agent
        from app.moderation.fingerprint import FingerprintHit

        cached = RiskAssessment(
            risk_level=RiskLevel.LOW,
            categories=[RiskCategory.AD],
            confidence=0.8,
            evidence=[Evidence(field="content", quote="日入三千", offset=0)],
            reasoning="历史结论",
        )
        hit = FingerprintHit(cached, "claude-opus-4-8", "simhash")
        with (
            patch("app.agents.moderation.llm.assess") as judge,
            patch("app.agents.moderation.llm.screen") as screen,
            patch("app.agents.moderation.fingerprint.lookup", return_value=hit),
        ):
            outcome = agent.assess("兼职", "日入三千，私信领取资料")
            judge.assert_not_called()
            screen.assert_not_called()
            self.assertTrue(outcome.model_version.startswith("fp-cache/"))
            assert outcome.assessment is not None
            self.assertEqual(outcome.assessment.categories, [RiskCategory.AD])
            self.assertEqual(outcome.assessment.evidence[0].offset, 0)

    def test_keyword_short_circuits_llm(self) -> None:
        from app.agents import moderation as agent

        with (
            patch("app.agents.moderation.llm.assess") as judge,
            patch("app.agents.moderation.llm.screen") as screen,
            patch("app.agents.moderation.fingerprint.remember") as remember,
            patch("app.agents.moderation.fingerprint.lookup", return_value=None),
        ):
            outcome = agent.assess("广告", "加微信买课")
            judge.assert_not_called()
            screen.assert_not_called()
            remember.assert_called_once()
            self.assertEqual(outcome.model_version, "keyword-regex")
            self.assertIsNotNone(outcome.assessment)
            assert outcome.assessment is not None
            self.assertEqual(outcome.assessment.categories, [RiskCategory.AD])
            self.assertEqual(outcome.token_cost, 0)

    def test_haiku_clean_skips_opus(self) -> None:
        from app.agents import moderation as agent

        screen_hit = ScreenOutcome(
            verdict="clean",
            confidence=0.9,
            reason="正常讨论",
            model_version="claude-haiku-4-5",
            latency_ms=80,
            token_cost=120,
            failed=False,
        )
        with (
            patch("app.agents.moderation.settings") as settings,
            patch("app.agents.moderation.llm.screen", return_value=screen_hit),
            patch("app.agents.moderation.llm.assess") as judge,
            patch("app.agents.moderation.fingerprint.lookup", return_value=None),
            patch("app.agents.moderation.fingerprint.remember"),
        ):
            settings.screen_model = "claude-haiku-4-5"
            settings.screen_accept_confidence = 0.75
            settings.model = "claude-opus-4-8"
            outcome = agent.assess("求助", "请问这个错误怎么修")
            judge.assert_not_called()
            self.assertEqual(outcome.model_version, "claude-haiku-4-5")
            assert outcome.assessment is not None
            self.assertEqual(outcome.assessment.risk_level, RiskLevel.NONE)
            self.assertEqual(outcome.token_cost, 120)

    def test_suspicious_haiku_goes_to_opus(self) -> None:
        from app.agents import moderation as agent

        screen_hit = ScreenOutcome(
            verdict="suspicious",
            confidence=0.4,
            reason="像广告",
            model_version="claude-haiku-4-5",
            latency_ms=80,
            token_cost=100,
            failed=False,
        )
        judged = AssessOutcome(
            assessment=RiskAssessment(
                risk_level=RiskLevel.LOW,
                categories=[RiskCategory.AD],
                confidence=0.8,
                evidence=[],
                reasoning="精判确认引流",
            ),
            degraded=False,
            degrade_reason=None,
            model_version="claude-opus-4-8",
            latency_ms=400,
            token_cost=800,
        )
        with (
            patch("app.agents.moderation.settings") as settings,
            patch("app.agents.moderation.llm.screen", return_value=screen_hit),
            patch("app.agents.moderation.llm.assess", return_value=judged) as judge,
            patch("app.agents.moderation.fingerprint.lookup", return_value=None),
            patch("app.agents.moderation.fingerprint.remember"),
        ):
            settings.screen_model = "claude-haiku-4-5"
            settings.screen_accept_confidence = 0.75
            settings.model = "claude-opus-4-8"
            outcome = agent.assess("有课", "私我，日入过万")
            judge.assert_called_once()
            self.assertEqual(outcome.model_version, "claude-opus-4-8")
            self.assertEqual(outcome.token_cost, 900)


if __name__ == "__main__":
    unittest.main()
