"""LangGraph 内容治理编排：三路并行、规则冲突检测、单次定向复核。"""

from __future__ import annotations

import operator
import time
from typing import Annotated, TypedDict

from langgraph.graph import END, START, StateGraph

from .agents import extraction, moderation, recheck as recheck_agent
from .config import settings
from .retrieval import vector_store
from .schemas import (
    Conflict,
    ContentType,
    GovernanceResponse,
    ModerateRequest,
    PostExtraction,
    RetrievalResult,
    RiskAssessment,
    RiskCategory,
    RiskLevel,
)


class GovernanceState(TypedDict, total=False):
    event: ModerateRequest
    retrieval_query: str
    extraction: PostExtraction | None
    retrieval: RetrievalResult
    assessment: RiskAssessment | None
    conflicts: list[Conflict]
    retry_count: int
    summary: str
    degraded_reasons: Annotated[list[str], operator.add]
    model_versions: Annotated[list[str], operator.add]
    token_cost: Annotated[int, operator.add]


def _dispatch(state: GovernanceState) -> dict:
    event = state["event"]
    if event.post_id <= 0 or event.bar_id <= 0 or not event.content.strip():
        raise ValueError("post_id、bar_id 必须为正数且 content 不能为空")
    query = event.title.strip() or event.content.strip()[:200]
    return {"retrieval_query": query}


def _extract(state: GovernanceState) -> dict:
    event = state["event"]
    outcome = extraction.extract(event.title, event.content, event.image_urls)
    return {
        "extraction": outcome.extraction,
        "degraded_reasons": [outcome.reason] if outcome.reason else [],
        "model_versions": [outcome.model_version],
        "token_cost": outcome.token_cost,
    }


def _retrieve(state: GovernanceState) -> dict:
    event = state["event"]
    try:
        result = vector_store.search(
            event.bar_id, state["retrieval_query"], event.post_id, settings.retrieval_k
        )
        return {"retrieval": result}
    except Exception as exc:  # noqa: BLE001
        return {
            "retrieval": RetrievalResult(),
            "degraded_reasons": [f"检索降级：{exc}"],
        }


def _review(state: GovernanceState) -> dict:
    event = state["event"]
    outcome = moderation.assess(event.title, event.content)
    assessment = outcome.assessment
    reasons = [outcome.degrade_reason] if outcome.degrade_reason else []
    if assessment is None:
        assessment = RiskAssessment(
            risk_level=RiskLevel.NONE,
            categories=[],
            confidence=0,
            evidence=[],
            reasoning="审核模型未返回结论，需人工复核",
        )
        reasons.append("审核无有效结论，已标记需人工")
    return {
        "assessment": assessment,
        "degraded_reasons": reasons,
        "model_versions": [outcome.model_version],
        "token_cost": outcome.token_cost,
    }


def _detect(state: GovernanceState) -> dict:
    assessment = state["assessment"]
    extraction_result = state.get("extraction")
    retrieval = state.get("retrieval", RetrievalResult())
    if assessment is None:
        return {"conflicts": []}

    conflicts: list[Conflict] = []
    if assessment.risk_level is not RiskLevel.NONE and not assessment.evidence:
        conflicts.append(
            Conflict(
                type="依据缺失",
                claim=assessment.reasoning,
                question="若仍认为违规，请给出原文连续子串；否则改判无风险",
            )
        )
    strong_cases = [item for item in retrieval.similar_posts if item.score >= 0.75]
    if (
        assessment.risk_level is not RiskLevel.NONE
        and strong_cases
        and all(item.historical_action == "未处置" for item in strong_cases)
    ):
        conflicts.append(
            Conflict(
                type="审核 vs 判例",
                claim=assessment.reasoning,
                anchors=assessment.evidence,
                counter="高相似判例均未处置",
                question="相似判例都未处置，原文哪一处仍足以支持违规",
            )
        )
    if (
        extraction_result
        and extraction_result.content_type in {ContentType.HELP, ContentType.DISCUSSION}
        and RiskCategory.AD in assessment.categories
    ):
        conflicts.append(
            Conflict(
                type="抽取 vs 审核",
                claim=assessment.reasoning,
                anchors=assessment.evidence,
                counter=f"内容类型为{extraction_result.content_type.value}",
                question="这是求助或讨论，还是广告；请引用原文说明",
            )
        )
    if assessment.risk_level is not RiskLevel.NONE and assessment.confidence < 0.7:
        conflicts.append(
            Conflict(
                type="置信度不足",
                claim=assessment.reasoning,
                anchors=assessment.evidence,
                question="维持该等级还是改判；必须重给原文引用",
            )
        )
    return {"conflicts": conflicts}


def _route_after_detect(state: GovernanceState) -> str:
    return "recheck" if state.get("conflicts") and state.get("retry_count", 0) == 0 else "report"


def _recheck(state: GovernanceState) -> dict:
    """只把冲突问题、原文和当前结论交给定向复核 Agent。"""
    event = state["event"]
    outcome = recheck_agent.recheck(
        event.title,
        event.content,
        state.get("conflicts", []),
        state["assessment"],
    )
    reasons = [outcome.reason] if outcome.reason else []
    assessment = state["assessment"]
    if outcome.assessment is not None:
        assessment = outcome.assessment
    else:
        reasons.append("定向复核失败，保留第一遍结论并转人工")
    return {
        "assessment": assessment,
        "retry_count": 1,
        "degraded_reasons": reasons,
        "model_versions": [outcome.model_version],
        "token_cost": outcome.token_cost,
    }


def _report(state: GovernanceState) -> dict:
    event = state["event"]
    extracted = state.get("extraction")
    summary = extracted.summary if extracted else " ".join(event.content.split())[:100]
    try:
        vector_store.upsert_post(event.post_id, event.bar_id, event.title, event.content)
        reasons: list[str] = []
    except Exception as exc:  # noqa: BLE001
        reasons = [f"向量写入降级：{exc}"]
    return {"summary": summary, "degraded_reasons": reasons}


def _build_graph():
    builder = StateGraph(GovernanceState)
    builder.add_node("dispatch", _dispatch)
    builder.add_node("extract", _extract)
    builder.add_node("retrieve", _retrieve)
    builder.add_node("review", _review)
    builder.add_node("detect", _detect)
    builder.add_node("recheck", _recheck)
    builder.add_node("report", _report)
    builder.add_edge(START, "dispatch")
    builder.add_edge("dispatch", "extract")
    builder.add_edge("dispatch", "retrieve")
    builder.add_edge("dispatch", "review")
    builder.add_edge(["extract", "retrieve", "review"], "detect")
    builder.add_conditional_edges(
        "detect", _route_after_detect, {"recheck": "recheck", "report": "report"}
    )
    builder.add_edge("recheck", "report")
    builder.add_edge("report", END)
    return builder.compile()


graph = _build_graph()

_CONFLICT_PRIORITY = {
    "依据缺失": 1,
    "规范不一致": 2,
    "审核 vs 判例": 3,
    "抽取 vs 审核": 4,
    "置信度不足": 5,
}


def govern(request: ModerateRequest) -> GovernanceResponse:
    started = time.monotonic()
    state = graph.invoke(
        {
            "event": request,
            "retry_count": 0,
            "degraded_reasons": [],
            "model_versions": [],
            "token_cost": 0,
        }
    )
    conflicts = state.get("conflicts", [])
    primary = min(
        conflicts, key=lambda item: _CONFLICT_PRIORITY.get(item.type, 99), default=None
    )
    reasons = list(dict.fromkeys(filter(None, state.get("degraded_reasons", []))))
    versions = list(dict.fromkeys(filter(None, state.get("model_versions", []))))
    return GovernanceResponse(
        post_id=request.post_id,
        summary=state.get("summary", ""),
        extraction=state.get("extraction"),
        retrieval=state.get("retrieval", RetrievalResult()),
        assessment=state.get("assessment"),
        conflicts=conflicts,
        has_conflict=bool(conflicts),
        conflict_type=primary.type if primary else None,
        retry_count=state.get("retry_count", 0),
        degraded=bool(reasons),
        degraded_reasons=reasons,
        degrade_reason="；".join(reasons) or None,
        model_version=" + ".join(versions),
        latency_ms=int((time.monotonic() - started) * 1000),
        token_cost=state.get("token_cost", 0),
    )
