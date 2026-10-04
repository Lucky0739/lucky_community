"""漏斗第 ① 层：本地敏感词正则。

命中即出结论，不进 Redis、不进 LLM。规则词本身就是原文连续子串，
满足「无引用不下结论」。广告类用可插空格的正则，拦「加 微 信」这种拆字。

没命中返回 None，交给第 ② 层指纹缓存。
"""

from __future__ import annotations

import re

from ..schemas import Evidence, RiskAssessment, RiskCategory, RiskLevel

# (分类, 风险等级, 正则)。广告词允许字间空白，对应引流话术的拆字变体。
_RULES: list[tuple[RiskCategory, RiskLevel, tuple[str, ...]]] = [
    (RiskCategory.ILLEGAL, RiskLevel.HIGH, (r"冰毒", r"海洛因", r"卖枪", r"枪支")),
    (RiskCategory.PORNOGRAPHY, RiskLevel.MEDIUM, (r"色情视频", r"约炮", r"裸聊")),
    (RiskCategory.ABUSE, RiskLevel.MEDIUM, (r"去死", r"人肉搜索")),
    (
        RiskCategory.AD,
        RiskLevel.LOW,
        (r"加\s*微\s*信", r"加\s*v信", r"加\s*v[xX]", r"加\s*QQ", r"刷单", r"代刷"),
    ),
    (RiskCategory.PROVOCATION, RiskLevel.LOW, (r"地域黑",)),
]

_COMPILED: list[tuple[RiskCategory, RiskLevel, list[re.Pattern[str]]]] = [
    (category, level, [re.compile(p) for p in patterns])
    for category, level, patterns in _RULES
]

_LEVEL_RANK = {
    RiskLevel.NONE: 0,
    RiskLevel.LOW: 1,
    RiskLevel.MEDIUM: 2,
    RiskLevel.HIGH: 3,
}

# 正则是确定性命中，不是 LLM 降级。置信度给高，但仍 < 1，留人工抽检空间。
_HIT_CONFIDENCE = 0.95


def assess(title: str, content: str) -> RiskAssessment | None:
    """原文命中规则则返回结论；否则 None。"""
    hits: list[tuple[RiskCategory, RiskLevel, str, str, int]] = []
    for category, level, patterns in _COMPILED:
        found = False
        for pattern in patterns:
            for field, text in (("title", title), ("content", content)):
                matched = pattern.search(text)
                if matched:
                    quote = matched.group(0)
                    hits.append((category, level, field, quote, matched.start()))
                    found = True
                    break
            if found:
                break

    if not hits:
        return None

    level = max((h[1] for h in hits), key=lambda lv: _LEVEL_RANK[lv])
    categories: list[RiskCategory] = []
    evidence: list[Evidence] = []
    quotes: list[str] = []
    for category, _, field, quote, offset in hits:
        if category not in categories:
            categories.append(category)
        evidence.append(Evidence(field=field, quote=quote, offset=offset))
        quotes.append(quote)

    shown = "、".join(quotes[:4])
    return RiskAssessment(
        risk_level=level,
        categories=categories,
        confidence=_HIT_CONFIDENCE,
        evidence=evidence,
        reasoning=f"本地敏感词规则命中：{shown}",
    )
