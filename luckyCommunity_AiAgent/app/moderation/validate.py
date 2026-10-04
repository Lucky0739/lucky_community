"""把 LLM 的原始输出收敛成可信结论。

两层职责：

1. `normalize()` —— 取值归一化。实测结构化解构输出约束在本模型上不生效，
   模型会给出 `高风险`、甚至把「广告引流」这个分类填进等级字段。
   这一步负责把能救的救回来，救不回来的按保守策略兜底。
2. `validate_evidence()` —— 文档 4.4 硬约束的服务端实现。引用必须在原文里
   真能找到，否则丢弃；引用全部被丢且还给了风险等级，就降级为「无风险」。

这些校验**不能只写在提示词里**——模型幻觉出一个很像原文但实际不存在的引用，
是完全可能的，只有拿原文做子串匹配才能拦住。

本模块是纯函数，不碰网络也不碰数据库，可直接单测。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ..schemas import (
    Evidence,
    RawAssessment,
    RiskAssessment,
    RiskCategory,
    RiskLevel,
)

# 等级别名。模型经常把「高」写成「高风险」、把「无风险」写成「安全」。
_LEVEL_ALIASES: dict[str, RiskLevel] = {
    "无风险": RiskLevel.NONE,
    "无": RiskLevel.NONE,
    "安全": RiskLevel.NONE,
    "正常": RiskLevel.NONE,
    "none": RiskLevel.NONE,
    "低": RiskLevel.LOW,
    "低风险": RiskLevel.LOW,
    "low": RiskLevel.LOW,
    "中": RiskLevel.MEDIUM,
    "中风险": RiskLevel.MEDIUM,
    "中等": RiskLevel.MEDIUM,
    "medium": RiskLevel.MEDIUM,
    "高": RiskLevel.HIGH,
    "高风险": RiskLevel.HIGH,
    "严重": RiskLevel.HIGH,
    "high": RiskLevel.HIGH,
}

# 分类别名。键是模型的常见写法，值是规范取值。
_CATEGORY_ALIASES: dict[str, RiskCategory] = {
    **{c.value: c for c in RiskCategory},
    "广告": RiskCategory.AD,
    "引流": RiskCategory.AD,
    "辱骂": RiskCategory.ABUSE,
    "攻击": RiskCategory.ABUSE,
    "色情": RiskCategory.PORNOGRAPHY,
    "低俗": RiskCategory.PORNOGRAPHY,
    "侵权": RiskCategory.INFRINGEMENT,
    "引战": RiskCategory.PROVOCATION,
    "挑动对立": RiskCategory.PROVOCATION,
    "违法": RiskCategory.ILLEGAL,
    "涉政": RiskCategory.ILLEGAL,
}

# 等级识别失败时的兜底等级。
# 为什么不兜底成「无风险」：那等于把一次失败的审核当成放行，最危险的错法。
# 兜底成「中」会让它进入人工复核视野，符合文档 6.2「标记需人工」的要求。
_FALLBACK_LEVEL = RiskLevel.MEDIUM

_FALLBACK_CONFIDENCE = 0.0


def _map_level(raw: str) -> RiskLevel | None:
    """把模型的等级写法映射成规范取值，映射不了返回 None。"""
    key = raw.strip().lower()
    if not key:
        return None
    if key in _LEVEL_ALIASES:
        return _LEVEL_ALIASES[key]
    # 兜底包含匹配：模型可能写成「风险等级：高」这种带前缀的
    for token, level in (("无风险", RiskLevel.NONE), ("低", RiskLevel.LOW),
                         ("中", RiskLevel.MEDIUM), ("高", RiskLevel.HIGH)):
        if token in key:
            return level
    return None


def _map_category(raw: str) -> RiskCategory | None:
    return _CATEGORY_ALIASES.get(raw.strip())


@dataclass(frozen=True)
class NormalizeResult:
    assessment: RiskAssessment
    warnings: list[str] = field(default_factory=list)
    level_unrecognized: bool = False


def normalize(raw: RawAssessment) -> NormalizeResult:
    """把宽松的原始输出收敛成规范的 RiskAssessment。

    `level_unrecognized=True` 表示等级字段彻底没救（空值或无法识别）。
    此时按 `_FALLBACK_LEVEL` 保守兜底并把置信度清零，标记为需人工——
    **不要**把它当成「无风险」放过去。
    """
    warnings: list[str] = []

    level = _map_level(raw.risk_level)
    level_unrecognized = level is None
    if level_unrecognized:
        warnings.append(
            f"风险等级无法识别（原始值={raw.risk_level!r}），已按"
            f"「{_FALLBACK_LEVEL.value}」保守兜底并清零置信度"
        )
        level = _FALLBACK_LEVEL

    categories: list[RiskCategory] = []
    for item in raw.categories:
        mapped = _map_category(item)
        if mapped is None:
            warnings.append(f"未知风险分类已丢弃：{item!r}")
            continue
        if mapped not in categories:
            categories.append(mapped)

    confidence = min(max(raw.confidence, 0.0), 1.0)
    if confidence != raw.confidence:
        warnings.append(f"置信度 {raw.confidence} 越界，已截断到 {confidence}")

    if level_unrecognized:
        confidence = _FALLBACK_CONFIDENCE

    # 等级为「无风险」时分类必须为空，否则下游会看到自相矛盾的记录
    if level is RiskLevel.NONE and categories:
        warnings.append("等级为无风险但给了分类，已清空分类")
        categories = []

    evidence = [
        Evidence(field=e.field, quote=e.quote, offset=e.offset)
        for e in raw.evidence
        if e.quote.strip()
    ]

    reasoning = raw.reasoning.strip() or "（模型未给出理由）"
    if level_unrecognized:
        reasoning = f"{reasoning}｜注：原始风险等级无法识别，已保守处理，需人工复核"

    return NormalizeResult(
        assessment=RiskAssessment(
            risk_level=level,
            categories=categories,
            confidence=confidence,
            evidence=evidence,
            reasoning=reasoning[:512],
        ),
        warnings=warnings,
        level_unrecognized=level_unrecognized,
    )


@dataclass(frozen=True)
class ValidationResult:
    """校验后的结论，以及是否因为校验而发生了降级。"""

    assessment: RiskAssessment
    downgraded: bool
    discarded_quotes: list[str]


def _field_text(title: str, content: str) -> dict[str, str]:
    return {"title": title, "content": content}


def validate_evidence(
    assessment: RiskAssessment, title: str, content: str
) -> ValidationResult:
    """校验每一条引用的真实性，并按约束一决定是否降级。

    处理规则：

    1. `field` 不是 title/content → 丢弃
    2. `quote` 不是该字段原文的连续子串 → 丢弃，并记下被丢的内容便于排查
    3. 通过的引用，`offset` 由服务端**重新计算**——模型给的 offset 一律不信
    4. 校验后如果**一条都不剩，而 risk_level 又不是「无风险」** → 降级为无风险

    第 4 条是「无引用不下结论」的落点。注意它不是无条件降级：
    本来就判「无风险」且没给引用的，属于正常结果，不该被改动。
    """
    field_text = _field_text(title, content)

    kept: list[Evidence] = []
    discarded: list[str] = []

    for item in assessment.evidence:
        text = field_text.get(item.field)
        if text is None:
            discarded.append(f"{item.field}:{item.quote}")
            continue

        offset = text.find(item.quote)
        if offset < 0:
            discarded.append(f"{item.field}:{item.quote}")
            continue

        # 即使模型给了 offset 也以服务端算出的为准——LLM 数下标基本不可靠
        kept.append(item.model_copy(update={"offset": offset}))

    had_evidence = bool(assessment.evidence)
    needs_downgrade = not kept and assessment.risk_level is not RiskLevel.NONE

    if needs_downgrade:
        reason = (
            "无充分依据：引用的原文片段未通过校验（可能不存在于原文）"
            if had_evidence
            else "无充分依据：未提供任何原文引用"
        )
        return ValidationResult(
            assessment=assessment.model_copy(
                update={
                    "risk_level": RiskLevel.NONE,
                    "categories": [],
                    "confidence": 0.0,
                    "evidence": [],
                    "reasoning": reason,
                }
            ),
            downgraded=True,
            discarded_quotes=discarded,
        )

    return ValidationResult(
        assessment=assessment.model_copy(update={"evidence": kept}),
        downgraded=False,
        discarded_quotes=discarded,
    )
