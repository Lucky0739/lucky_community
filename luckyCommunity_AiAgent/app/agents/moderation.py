"""帖子审核 Agent：三层漏斗，全部用现有正则 / Redis / Claude，不引新中间件。

① 本地敏感词正则（微秒，零成本）——命中直接出结论
② 内容指纹缓存（原文 SHA256 + SimHash LSH 走已有 Redis）
     精确命中复用任意历史结论；近邻只复用「有风险」结论
     垃圾广告是高度重复的模板话术，指纹复用既省钱，又天然对刷屏更敏感
③ Haiku 粗筛 → 只有可疑的才上 Opus 精判

`POST /moderate` 契约不动。
"""

from __future__ import annotations

import logging
import time

from ..config import settings
from ..moderation import fingerprint, keywords, llm
from ..moderation.llm import AssessOutcome
from ..moderation.validate import validate_evidence
from ..schemas import RiskAssessment, RiskLevel

logger = logging.getLogger(__name__)

_KEYWORD_MODEL = "keyword-regex"


def _elapsed_ms(started: float) -> int:
    return int((time.monotonic() - started) * 1000)


def _finish(
    assessment: RiskAssessment | None,
    *,
    degraded: bool,
    reason: str | None,
    model_version: str,
    started: float,
    token_cost: int,
) -> AssessOutcome:
    return AssessOutcome(
        assessment=assessment,
        degraded=degraded,
        degrade_reason=reason,
        model_version=model_version,
        latency_ms=_elapsed_ms(started),
        token_cost=token_cost,
    )


def assess(title: str, content: str) -> AssessOutcome:
    started = time.monotonic()
    keyword = keywords.assess(title, content)
    if keyword is not None:
        fingerprint.remember(title, content, keyword, _KEYWORD_MODEL)
        logger.info("审核漏斗命中第①层敏感词")
        return _finish(
            keyword,
            degraded=False,
            reason=None,
            model_version=_KEYWORD_MODEL,
            started=started,
            token_cost=0,
        )

    cached = fingerprint.lookup(title, content)
    if cached is not None:
        checked = validate_evidence(cached.assessment, title, content)
        if not checked.downgraded:
            logger.info("审核漏斗命中第②层指纹 match=%s", cached.match)
            return _finish(
                checked.assessment,
                degraded=False,
                reason=None,
                model_version=f"fp-cache/{cached.model_version}",
                started=started,
                token_cost=0,
            )
        logger.info("指纹近邻引用对不上当前原文，视为未命中")

    extra_tokens = 0
    if settings.screen_model:
        screened = llm.screen(title, content)
        extra_tokens += screened.token_cost
        if (
            not screened.failed
            and screened.verdict == "clean"
            and screened.confidence >= settings.screen_accept_confidence
        ):
            assessment = RiskAssessment(
                risk_level=RiskLevel.NONE,
                categories=[],
                confidence=screened.confidence,
                evidence=[],
                reasoning=screened.reason or "粗筛判定无明显风险",
            )
            fingerprint.remember(title, content, assessment, screened.model_version)
            logger.info("审核漏斗 Haiku 粗筛放行")
            return _finish(
                assessment,
                degraded=False,
                reason=None,
                model_version=screened.model_version,
                started=started,
                token_cost=extra_tokens,
            )

    outcome = llm.assess(title, content)
    if outcome.assessment is not None and not outcome.degraded:
        fingerprint.remember(title, content, outcome.assessment, outcome.model_version)

    return _finish(
        outcome.assessment,
        degraded=outcome.degraded,
        reason=outcome.degrade_reason,
        model_version=outcome.model_version or settings.model,
        started=started,
        token_cost=extra_tokens + outcome.token_cost,
    )
