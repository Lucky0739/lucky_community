"""帖子抽取 Agent：结构化输出失败时退回可追溯的本地摘要。"""

from __future__ import annotations

import time
from dataclasses import dataclass

from langchain_core.messages import HumanMessage, SystemMessage

from ..config import settings
from ..moderation.llm import get_model
from ..moderation.prompt_store import get_prompt
from ..schemas import ContentType, PostExtraction


@dataclass(frozen=True)
class ExtractionOutcome:
    extraction: PostExtraction
    degraded: bool
    reason: str | None
    model_version: str
    latency_ms: int
    token_cost: int


_SYSTEM_KEY = "extraction.system"
_USER_KEY = "extraction.user"
_SYSTEM = """你是社区帖子信息抽取模块。只压缩和归纳原文，不得引入原文没有的事实。
输出标题、100字以内摘要、实体、话题、内容类型和语言。
content_type 只能是：求助、分享、讨论、提问、其他。"""
_USER = """<title>{{title}}</title>
<content>{{content}}</content>
<image_urls>{{image_urls}}</image_urls>"""


def _fallback(title: str, content: str, started: float, reason: str) -> ExtractionOutcome:
    compact = " ".join(content.split())
    generated_title = title.strip() or compact[:30] or "无标题"
    return ExtractionOutcome(
        extraction=PostExtraction(
            title=generated_title,
            summary=compact[:100],
            entities=[],
            topics=[],
            content_type=ContentType.OTHER,
            language="zh",
        ),
        degraded=True,
        reason=reason,
        model_version="local-fallback",
        latency_ms=int((time.monotonic() - started) * 1000),
        token_cost=0,
    )


def extract(title: str, content: str, image_urls: list[str]) -> ExtractionOutcome:
    started = time.monotonic()
    try:
        model = get_model(timeout=3, max_tokens=768)
        chain = model.with_structured_output(
            PostExtraction,
            include_raw=True,
            method=settings.structured_method,
        )
        image_context = "\n".join(image_urls[:5]) or "无"
        system_prompt = get_prompt(_SYSTEM_KEY, _SYSTEM)
        user_prompt = get_prompt(_USER_KEY, _USER)
        user_content = (
            user_prompt.content.replace("{{title}}", title)
            .replace("{{content}}", content)
            .replace("{{image_urls}}", image_context)
        )
        chain = chain.with_config(
            run_name="content_extraction",
            metadata={
                "system_prompt_key": system_prompt.prompt_key,
                "system_prompt_version": system_prompt.version,
                "system_prompt_checksum": system_prompt.checksum,
                "system_prompt_source": system_prompt.source,
                "user_prompt_key": user_prompt.prompt_key,
                "user_prompt_version": user_prompt.version,
                "user_prompt_checksum": user_prompt.checksum,
                "user_prompt_source": user_prompt.source,
            },
        )
        output = chain.invoke(
            [
                SystemMessage(content=system_prompt.content),
                HumanMessage(content=user_content),
            ]
        )
        parsed = output.get("parsed")
        if not isinstance(parsed, PostExtraction):
            return _fallback(title, content, started, "抽取模型未返回合法结构")
        parsed = parsed.model_copy(
            update={
                "title": parsed.title.strip() or title.strip() or content[:30],
                "summary": parsed.summary.strip()[:100],
            }
        )
        raw = output.get("raw")
        usage = getattr(raw, "usage_metadata", None) or {}
        tokens = usage.get("total_tokens") or (
            (usage.get("input_tokens") or 0) + (usage.get("output_tokens") or 0)
        )
        meta = getattr(raw, "response_metadata", None) or {}
        return ExtractionOutcome(
            extraction=parsed,
            degraded=False,
            reason=None,
            model_version=str(meta.get("model") or "extraction-model"),
            latency_ms=int((time.monotonic() - started) * 1000),
            token_cost=int(tokens),
        )
    except Exception as exc:  # noqa: BLE001
        return _fallback(title, content, started, f"抽取失败：{exc}")
