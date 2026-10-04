"""LangChain 审核链：ChatPromptTemplate | ChatAnthropic，再走防御式解析。

<h3>结论怎么拿到</h3>
先用 LangChain 的结构化输出（`with_structured_output`，走强制工具调用），
拿不到再退回「`extract_json` 剥壳 + `Raw*.model_validate`」。两条路都通向
`validate.normalize`，所以下游只认归一化后的结果，不关心结论是怎么拿到的。

**为什么两道都留着**：最初用 Anthropic 的 `output_format` 做 schema 约束时
**实测在 Opus 上并没有真正生效**——模型返回带 ```json 围栏的 markdown、
`risk_level` 出现枚举外的「高风险」、甚至把「广告引流」这个**分类**值填进等级字段。
强制工具调用的约束比 `output_format` 强得多，但「更强」不等于「不会失效」，
而这里失效的代价是一次审核被降级成「建议转人工」。多留一条兜底几乎不花钱。

<h3>为什么必须 include_raw</h3>
结构化输出默认只返回解析后的对象，而 `stop_reason`（拒答 / 截断）和 token 用量
都挂在原始 `AIMessage` 上。**拒答时 API 返回的是 HTTP 200 + 空内容**，
不看 `stop_reason` 就会把拒答当成「模型没给出风险」放过去——这是最危险的一种错法。
`include_raw=True` 让两者兼得。

HTTP 契约、Java 落库都不依赖本文件的实现细节。
"""

from __future__ import annotations

import json
import logging
import os
import re
import time
from dataclasses import dataclass

from langchain_anthropic import ChatAnthropic
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel

from ..config import settings
from ..schemas import (
    RawAssessment,
    RawScreen,
    RiskAssessment,
    StructuredAssessment,
    StructuredScreen,
)
from .prompt_store import PromptValue
from .prompts import (
    load_judge_system_prompt,
    load_screen_system_prompt,
    load_user_prompt,
)
from .validate import normalize, validate_evidence

logger = logging.getLogger(__name__)

# 匹配 ```json ... ``` 或 ``` ... ``` 围栏
_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)\s*```", re.DOTALL)

# 结构化输出的实现方式，由 settings.structured_method 决定（见 config.py）：
# `function_calling` = 强制模型调用一个工具（约束最强，也是 langchain-anthropic 的默认）。
#   仅适用于 Anthropic 官方端点。DeepSeek 的 Anthropic 兼容端点在 thinking 模式下
#   直接拒绝强制 tool_choice（400 `Thinking mode does not support this tool_choice`），
#   用了它每次调用都在 _invoke 层被拦下，`extract_json` 兜底根本走不到。
# `json_schema` = 原生 output_format。当初在 Opus 上实测没生效（见模块 docstring），
#   但在 DeepSeek 上实测正常，所以切到 DeepSeek 后它是默认值。
_models: dict[str, ChatAnthropic] = {}
_prompt = ChatPromptTemplate.from_messages(
    [
        ("system", "{system}"),
        ("human", "{user_prompt}"),
    ]
)


@dataclass(frozen=True)
class AssessOutcome:
    """一次审核调用的结果。

    `assessment is None` 表示没拿到结论；否则即使 `degraded=True` 也是有结论的
    （只是被归一化兜底或被引用校验降级了）。
    """

    assessment: RiskAssessment | None
    degraded: bool
    degrade_reason: str | None
    model_version: str
    latency_ms: int
    token_cost: int


@dataclass(frozen=True)
class ScreenOutcome:
    """Haiku 粗筛结果。failed / 非 clean 都交给 Opus，绝不把失败当成放行。"""

    verdict: str
    confidence: float
    reason: str
    model_version: str
    latency_ms: int
    token_cost: int
    failed: bool


def _resolved_api_key() -> str:
    """按优先级找出可用的 key。

    `ANTHROPIC_AUTH_TOKEN` 这一层是必要的：langchain-anthropic **只认**
    `ANTHROPIC_API_KEY` 或构造时的 `api_key=`，看到 `AUTH_TOKEN` 会直接抛
    「no API key or authorization credentials were provided」。而这个变量
    正是把服务指向兼容端点时最常用的一个（Claude Code 也用它）。
    """
    return (
        settings.api_key
        or os.getenv("ANTHROPIC_API_KEY", "")
        or os.getenv("ANTHROPIC_AUTH_TOKEN", "")
    )


def get_model(
    name: str | None = None,
    *,
    timeout: float | None = None,
    max_tokens: int | None = None,
    effort: str | None = None,
) -> ChatAnthropic:
    """按模型名惰性构造 ChatAnthropic。Haiku / Opus 各缓存一份。

    timeout / max_retries 刻意收紧：Java RestClient 读超时只有 8s，
    这里重试把时间打满会让 Java 先超时、Python 还在跑，两边状态对不上。
    """
    model_name = name or settings.model
    cache_key = f"{model_name}:{timeout}:{max_tokens}:{effort}"
    cached = _models.get(cache_key)
    if cached is not None:
        return cached

    kwargs: dict = {
        "model": model_name,
        "max_tokens": max_tokens if max_tokens is not None else settings.max_tokens,
        "temperature": 0,
        "timeout": timeout if timeout is not None else settings.judge_timeout,
        "max_retries": 0,
    }
    if effort:
        kwargs["effort"] = effort
    api_key = _resolved_api_key()
    if api_key:
        kwargs["api_key"] = api_key
    # 空 = 官方端点，不传该参数（传了也要走默认值，多此一举）。
    if settings.base_url:
        kwargs["base_url"] = settings.base_url
    # 只有显式配成 false 才发。None（没配）什么都不发，保持官方端点默认行为。
    # 关闭 thinking 是必须的：思考内容计入 max_tokens，开着时粗筛的 256
    # 会被吃光、输出被截断成非法 JSON（实测 out_tokens 直接打满 256）。
    if settings.thinking is False:
        kwargs["thinking"] = {"type": "disabled"}
    model = ChatAnthropic(**kwargs)
    _models[cache_key] = model
    return model


def get_chain(
    name: str | None = None,
    *,
    schema: type[BaseModel] = StructuredAssessment,
    timeout: float | None = None,
    max_tokens: int | None = None,
    effort: str | None = None,
    run_name: str = "moderation_llm",
    prompt_metadata: dict[str, str | int] | None = None,
):
    """拼出「提示词 → 强制结构化输出」的链。

    `include_raw=True` 让输出变成 `{"raw": AIMessage, "parsed": 对象 | None,
    "parsing_error": 异常 | None}`。解析失败**不抛异常**，而是 `parsed=None`
    并把异常塞进 `parsing_error`——调用方因此有机会退回手写解析，
    而不是因为一次格式偏差就丢掉整次审核。
    """
    model = get_model(name, timeout=timeout, max_tokens=max_tokens, effort=effort)
    structured = model.with_structured_output(
        schema, include_raw=True, method=settings.structured_method
    )
    return (_prompt | structured).with_config(
        run_name=run_name,
        metadata=prompt_metadata or {},
    )


def credentials_available() -> bool:
    """启动自检用：能不能真的拿到 key。

    这里**不能**用「能否构造出 ChatAnthropic」来判断：ChatAnthropic 构造时
    根本不校验凭据，key 要到真正发请求时才用得上。所以那样写的自检恒为 True，
    服务照常起、/healthz 照常报 credentials: true，一直到第一次真实审核才 401
    ——曾经就是这个误报，让「缺凭据」看起来是「一切正常」。
    """
    return bool(_resolved_api_key())


def _elapsed_ms(started: float) -> int:
    return int((time.monotonic() - started) * 1000)


def _degraded(reason: str, started: float, model: str = "") -> AssessOutcome:
    return AssessOutcome(
        assessment=None,
        degraded=True,
        degrade_reason=reason,
        model_version=model,
        latency_ms=_elapsed_ms(started),
        token_cost=0,
    )


def extract_json(text: str) -> dict | None:
    """从模型输出里抠出 JSON 对象，容忍 markdown 围栏和前后废话。

    依次尝试：整段直接解析 → 剥掉代码围栏 → 取第一个 `{` 到最后一个 `}`。
    三种都失败返回 None，由调用方走降级。
    """
    candidates: list[str] = [text]

    fenced = _FENCE_RE.search(text)
    if fenced:
        candidates.append(fenced.group(1))

    start, end = text.find("{"), text.rfind("}")
    if start >= 0 and end > start:
        candidates.append(text[start : end + 1])

    for candidate in candidates:
        try:
            parsed = json.loads(candidate.strip())
        except (json.JSONDecodeError, ValueError):
            continue
        if isinstance(parsed, dict):
            return parsed
    return None


def _message_text(message: BaseMessage) -> str:
    """把 AIMessage.content 收成纯文本。content 可能是 str，也可能是块列表。"""
    content = message.content
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(block.get("text") or "")
            elif hasattr(block, "text"):
                parts.append(getattr(block, "text") or "")
        return "".join(parts)
    return str(content or "")


def _token_cost(message: AIMessage) -> int:
    usage = message.usage_metadata or {}
    total = usage.get("total_tokens")
    if isinstance(total, int):
        return total
    return (usage.get("input_tokens") or 0) + (usage.get("output_tokens") or 0)


@dataclass(frozen=True)
class _CallResult:
    """一次模型调用的全部产出。

    `error` 非空表示这次调用整个失败（网络 / 鉴权 / 超时），此时 `message` 为 None，
    调用方直接降级，不必再看别的字段。`error` 为空但 `parsed` 为 None 是另一回事：
    模型答了，只是没按 schema 答——这时还有手写解析可以抢救。
    """

    message: AIMessage | None
    parsed: BaseModel | None
    parsing_error: str | None
    model_version: str
    latency_ms: int
    token_cost: int
    error: str | None


def _invoke(
    *,
    schema: type[BaseModel],
    system: PromptValue,
    title: str,
    content: str,
    model_name: str,
    timeout: float,
    max_tokens: int,
    effort: str | None,
    run_name: str,
) -> _CallResult:
    """调一次模型，返回原始消息 + 结构化解析结果，异常一律收敛成 error。"""
    started = time.monotonic()
    try:
        user_prompt = load_user_prompt(title, content)
        out = get_chain(
            model_name,
            schema=schema,
            timeout=timeout,
            max_tokens=max_tokens,
            effort=effort,
            run_name=run_name,
            prompt_metadata={
                "system_prompt_key": system.prompt_key,
                "system_prompt_version": system.version,
                "system_prompt_checksum": system.checksum,
                "system_prompt_source": system.source,
                "user_prompt_key": user_prompt.prompt_key,
                "user_prompt_version": user_prompt.version,
                "user_prompt_checksum": user_prompt.checksum,
                "user_prompt_source": user_prompt.source,
            },
        ).invoke(
            {
                "system": system.content,
                "user_prompt": user_prompt.content,
            }
        )
    except Exception as e:  # noqa: BLE001 —— 兜底，绝不让异常冒到路由层
        logger.warning("LangChain 审核调用失败：%s", e)
        return _CallResult(
            message=None,
            parsed=None,
            parsing_error=None,
            model_version=model_name,
            latency_ms=_elapsed_ms(started),
            token_cost=0,
            error=f"LangChain 审核调用失败：{e}",
        )

    message = out.get("raw")
    if not isinstance(message, AIMessage):
        return _CallResult(
            message=None,
            parsed=None,
            parsing_error=None,
            model_version=model_name,
            latency_ms=_elapsed_ms(started),
            token_cost=0,
            error="LangChain 返回了非 AIMessage",
        )

    meta = message.response_metadata or {}
    parsing_error = out.get("parsing_error")
    return _CallResult(
        message=message,
        parsed=out.get("parsed"),
        parsing_error=str(parsing_error) if parsing_error else None,
        model_version=str(meta.get("model") or model_name),
        latency_ms=_elapsed_ms(started),
        token_cost=_token_cost(message),
        error=None,
    )


def _parse_assessment(result: _CallResult) -> tuple[RawAssessment | None, str | None]:
    """取得精判的宽松形态输出；拿不到就返回 (None, 失败原因)。

    优先用 LangChain 结构化解析出来的对象；它失败时退回 `extract_json` +
    `RawAssessment.model_validate`。这条兜底是刻意保留的，理由见模块 docstring——
    这里每救回一次，就少一次「建议转人工」。
    """
    if isinstance(result.parsed, RawAssessment):
        return result.parsed, None

    if result.parsing_error:
        logger.warning("结构化输出解析失败，退回手写解析：%s", result.parsing_error)

    text = _message_text(result.message) if result.message is not None else ""
    if not text.strip():
        return None, "模型返回了空内容"

    payload = extract_json(text)
    if payload is None:
        logger.warning("未能从响应中抠出 JSON，前 200 字：%s", text[:200])
        return None, "模型输出不是合法 JSON"

    try:
        return RawAssessment.model_validate(payload), None
    except Exception as e:  # noqa: BLE001
        logger.warning("原始输出结构异常：%s", e)
        return None, f"原始输出结构异常：{e}"


def _parse_screen(result: _CallResult) -> tuple[RawScreen | None, str | None]:
    """取得粗筛的宽松形态输出。兜底策略同 `_parse_assessment`。"""
    if isinstance(result.parsed, RawScreen):
        return result.parsed, None

    if result.parsing_error:
        logger.warning("粗筛结构化输出解析失败，退回手写解析：%s", result.parsing_error)

    text = _message_text(result.message) if result.message is not None else ""
    if not text.strip():
        return None, "粗筛输出不是合法 JSON"

    payload = extract_json(text)
    if payload is None:
        return None, "粗筛输出不是合法 JSON"

    try:
        return RawScreen.model_validate(payload), None
    except Exception as e:  # noqa: BLE001
        return None, f"粗筛输出不是合法 JSON：{e}"


def screen(title: str, content: str) -> ScreenOutcome:
    """Haiku 粗筛：只回答 clean / suspicious。失败一律当 suspicious，交给 Opus。"""
    model_name = settings.screen_model
    call = _invoke(
        schema=StructuredScreen,
        system=load_screen_system_prompt(),
        title=title,
        content=content,
        model_name=model_name,
        timeout=float(settings.screen_timeout),
        max_tokens=256,
        effort=None,
        run_name="moderation_screen",
    )
    if call.error or call.message is None:
        return ScreenOutcome(
            verdict="suspicious",
            confidence=0.0,
            reason=call.error or "粗筛失败",
            model_version=call.model_version,
            latency_ms=call.latency_ms,
            token_cost=call.token_cost,
            failed=True,
        )

    # `refusal` 是 Anthropic 安全分类器特有的 stop_reason，兼容端点（如 DeepSeek）
    # 不会发。在那种后端上这个分支是死代码，真被内容过滤时拿到的是空 content，
    # 会在下面 _parse_screen 解析失败后按 suspicious 处理——同样安全，只是
    # reason 不够具体。留着是因为它零成本，且切回官方端点立刻恢复作用。
    meta = call.message.response_metadata or {}
    if meta.get("stop_reason") == "refusal":
        return ScreenOutcome(
            "suspicious",
            0.0,
            "模型拒答",
            call.model_version,
            call.latency_ms,
            call.token_cost,
            True,
        )

    raw, why = _parse_screen(call)
    if raw is None:
        return ScreenOutcome(
            "suspicious",
            0.0,
            why or "粗筛输出不是合法 JSON",
            call.model_version,
            call.latency_ms,
            call.token_cost,
            True,
        )

    raw_verdict = raw.verdict.strip().lower()
    if raw_verdict in {"clean", "无风险", "pass", "safe"}:
        verdict = "clean"
    else:
        # 未知取值也当 suspicious，避免粗筛把违规放行
        verdict = "suspicious"

    # confidence 的浮点类型由 RawScreen 保证，这里只需截断到 [0,1]
    confidence = min(max(raw.confidence, 0.0), 1.0)
    reason = raw.reason.strip() or "粗筛未给出理由"

    return ScreenOutcome(
        verdict=verdict,
        confidence=confidence,
        reason=reason,
        model_version=call.model_version,
        latency_ms=call.latency_ms,
        token_cost=call.token_cost,
        failed=False,
    )


def assess(title: str, content: str) -> AssessOutcome:
    """调精判模型审一条帖子。

    本函数**不抛异常**：所有失败路径都收敛成 `degraded=True` 的返回，
    由调用方决定怎么落库。审核失败不该让上游发帖失败（文档 6.2）。
    """
    started = time.monotonic()
    model_name = settings.model
    # 变量名用 call 而不是 result：下面 validate_evidence() 的返回值也叫 result
    # （沿用原代码），而 outcome() 是个闭包、会晚于那次赋值才求值——
    # 两者重名会让闭包悄悄读到 ValidationResult，而不是这次的调用结果。
    call = _invoke(
        schema=StructuredAssessment,
        system=load_judge_system_prompt(),
        title=title,
        content=content,
        model_name=model_name,
        timeout=float(settings.judge_timeout),
        max_tokens=settings.max_tokens,
        effort=settings.effort,
        run_name="moderation_judge",
    )
    if call.error or call.message is None:
        return _degraded(
            call.error or "LangChain 审核调用失败", started, call.model_version
        )

    def outcome(
        assessment: RiskAssessment | None, degraded: bool, reason: str | None
    ) -> AssessOutcome:
        return AssessOutcome(
            assessment=assessment,
            degraded=degraded,
            degrade_reason=reason,
            model_version=call.model_version,
            latency_ms=call.latency_ms,
            token_cost=call.token_cost,
        )

    # ⚠️ 必须先判 stop_reason。审核任务要读色情/辱骂/违法内容，
    # 安全分类器可能直接拒答——此时是 HTTP 200 但 content 为空。
    # 注意 `refusal` 是 Anthropic 官方端点专有的取值，兼容端点上这一支不会命中；
    # 那种情况下拒答表现为空 content，由 _parse_assessment 兜成「模型返回了空内容」
    # 后降级，结论一样是不放行，只是原因描述没那么准。
    meta = call.message.response_metadata or {}
    stop_reason = meta.get("stop_reason")
    if stop_reason == "refusal":
        logger.warning("Claude 拒答本次审核，metadata=%s", meta)
        return outcome(None, True, "模型拒答，建议转人工")

    if stop_reason == "max_tokens":
        logger.warning("审核输出被 max_tokens 截断，当前上限 %s", settings.max_tokens)
        return outcome(None, True, "输出被截断，未能得到完整结论")

    raw, why = _parse_assessment(call)
    if raw is None:
        return outcome(None, True, why or "模型输出不是合法 JSON")

    normalized = normalize(raw)
    for warning in normalized.warnings:
        logger.warning("归一化：%s", warning)

    result = validate_evidence(normalized.assessment, title, content)
    if result.discarded_quotes:
        logger.warning(
            "丢弃了 %d 条未通过原文校验的引用：%s",
            len(result.discarded_quotes),
            result.discarded_quotes,
        )

    reasons: list[str] = []
    if normalized.level_unrecognized:
        reasons.append("风险等级无法识别，已保守兜底为「中」并转人工")
    if result.downgraded:
        reasons.append("引用未通过原文校验，已按「无引用不下结论」降级为无风险")

    return outcome(
        result.assessment,
        degraded=bool(reasons),
        reason="；".join(reasons) if reasons else None,
    )
