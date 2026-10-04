"""定向复核 Agent：只围绕已检测到的冲突修补审核结论。"""

from __future__ import annotations

import json
import time
from dataclasses import dataclass

from langchain_core.messages import HumanMessage, SystemMessage

from ..config import settings
from ..moderation.llm import get_model
from ..moderation.prompt_store import get_prompt
from ..moderation.validate import validate_evidence
from ..schemas import Conflict, RiskAssessment


@dataclass(frozen=True)
class RecheckOutcome:
    assessment: RiskAssessment | None
    reason: str | None
    model_version: str
    token_cost: int
    latency_ms: int


_SYSTEM_KEY = "recheck.system"
_USER_KEY = "recheck.user"
_SYSTEM = """你是内容治理的定向复核模块。你只能回答给定冲突问题并修补风控结论，
不得重做摘要或检索。风险结论必须引用标题或正文中的逐字连续子串；拿不出引用时判无风险。
返回一份完整 RiskAssessment。"""
_USER = """<title>{{title}}</title>
<content>{{content}}</content>
<current>{{current}}</current>
<conflicts>{{conflicts}}</conflicts>"""


def recheck(
    title: str,
    content: str,
    conflicts: list[Conflict],
    current: RiskAssessment,
) -> RecheckOutcome:
    started = time.monotonic()
    questions = [
        {
            "type": item.type,
            "question": item.question,
            "counter": item.counter,
        }
        for item in conflicts
    ]
    try:
        model = get_model(timeout=8, max_tokens=1024)
        chain = model.with_structured_output(
            RiskAssessment,
            include_raw=True,
            method=settings.structured_method,
        )
        system_prompt = get_prompt(_SYSTEM_KEY, _SYSTEM)
        user_prompt = get_prompt(_USER_KEY, _USER)
        user_content = (
            user_prompt.content.replace("{{title}}", title)
            .replace("{{content}}", content)
            .replace("{{current}}", current.model_dump_json())
            .replace(
                "{{conflicts}}",
                json.dumps(questions, ensure_ascii=False),
            )
        )
        chain = chain.with_config(
            run_name="moderation_recheck",
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
        if not isinstance(parsed, RiskAssessment):
            return RecheckOutcome(
                None,
                "定向复核未返回合法结构",
                "recheck-model",
                0,
                int((time.monotonic() - started) * 1000),
            )
        checked = validate_evidence(parsed, title, content)
        raw = output.get("raw")
        usage = getattr(raw, "usage_metadata", None) or {}
        tokens = usage.get("total_tokens") or (
            (usage.get("input_tokens") or 0) + (usage.get("output_tokens") or 0)
        )
        meta = getattr(raw, "response_metadata", None) or {}
        reason = (
            "定向复核引用未通过原文校验，已按无引用不下结论降级"
            if checked.downgraded
            else None
        )
        return RecheckOutcome(
            checked.assessment,
            reason,
            str(meta.get("model") or "recheck-model"),
            int(tokens),
            int((time.monotonic() - started) * 1000),
        )
    except Exception as exc:  # noqa: BLE001
        return RecheckOutcome(
            None,
            f"定向复核失败：{exc}",
            "recheck-model",
            0,
            int((time.monotonic() - started) * 1000),
        )
