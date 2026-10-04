"""结构化输出这条路的控制流测试。

全部用假的 chain 打桩，不碰网络——这里要验的是「拿到什么样的响应，就走到哪条分支」，
不是模型判得准不准。真模型行为要靠 `ai.toml` 里配好 key 之后手工验证。

重点覆盖的是**降级**：结构化输出失败必须能退回手写解析，两条都失败才算真失败，
而拒答（HTTP 200 + 空内容）必须走自己的分支、绝不能当成「模型没给风险」。
"""

from __future__ import annotations

import unittest
from contextlib import contextmanager
from unittest.mock import patch

from langchain_anthropic import convert_to_anthropic_tool
from langchain_core.messages import AIMessage

from app.moderation import llm
from app.schemas import (
    RawAssessment,
    RawEvidence,
    RiskCategory,
    RiskLevel,
    StructuredAssessment,
)


def _ai_message(
    text: str = "",
    *,
    stop_reason: str = "end_turn",
    total_tokens: int = 42,
) -> AIMessage:
    return AIMessage(
        content=text,
        response_metadata={"model": "claude-test-model", "stop_reason": stop_reason},
        usage_metadata={
            "input_tokens": 10,
            "output_tokens": 32,
            "total_tokens": total_tokens,
        },
    )


class _FakeChain:
    """顶替 `get_chain()` 的返回值。payload 是异常就抛，否则原样当 invoke 结果。"""

    def __init__(self, payload: object) -> None:
        self._payload = payload

    def invoke(self, _input: object) -> object:
        if isinstance(self._payload, Exception):
            raise self._payload
        return self._payload


@contextmanager
def _chain_returning(payload: object):
    with patch.object(llm, "get_chain", lambda *a, **k: _FakeChain(payload)):
        yield


def _structured_ok(**overrides) -> dict:
    """模拟结构化解析成功的返回值（include_raw=True 的形状）。

    这里走正常的构造器而不是 `model_copy(update=...)`——后者不触发校验，
    塞进去的 dict 不会被转成模型对象，测出来的就不是真实路径了。
    """
    fields: dict = {
        "risk_level": "高",
        "categories": ["广告引流"],
        "confidence": 0.9,
        "evidence": [],  # 空 evidence，交给 validate_evidence 决定降级
        "reasoning": "模板化引流话术",
    }
    fields.update(overrides)
    return {"raw": _ai_message(), "parsed": RawAssessment(**fields), "parsing_error": None}


class StructuredHappyPathTests(unittest.TestCase):
    def test_parsed_result_is_used_directly(self) -> None:
        """结构化解析成功时，直接拿它的结论，不该再碰正文。"""
        payload = _structured_ok(
            evidence=[RawEvidence(field="content", quote="加微信 abc", offset=-1)]
        )
        # 正文故意放一段无法解析的废话：只要结论来自 parsed 而不是正文，
        # 这段就永远不会被 extract_json 读到。
        payload["raw"] = _ai_message("正文里没有 JSON，只有一段废话")
        with _chain_returning(payload):
            outcome = llm.assess("兼职", "加微信 abc 日入三千")

        self.assertIsNotNone(outcome.assessment)
        assert outcome.assessment is not None
        self.assertEqual(outcome.assessment.risk_level, RiskLevel.HIGH)
        self.assertEqual(outcome.assessment.categories, [RiskCategory.AD])
        self.assertEqual(outcome.model_version, "claude-test-model")
        self.assertEqual(outcome.token_cost, 42)
        self.assertFalse(outcome.degraded)

    def test_verdict_without_evidence_still_downgrades(self) -> None:
        """工具调用给了「高」，但一条引用都没给 → 照样降级为无风险。

        「无引用不下结论」是服务端硬约束（文档 4.4）。换了拿结论的方式，
        这条约束必须原封不动——否则模型只要改走工具调用就能绕开它。
        """
        with _chain_returning(_structured_ok()):  # evidence 默认是空数组
            outcome = llm.assess("兼职", "加微信 abc 日入三千")

        self.assertIsNotNone(outcome.assessment)
        assert outcome.assessment is not None
        self.assertEqual(outcome.assessment.risk_level, RiskLevel.NONE)
        self.assertEqual(outcome.assessment.confidence, 0.0)
        self.assertTrue(outcome.degraded)

    def test_invalid_evidence_still_downgrades(self) -> None:
        """结构化输出也不能绕过原文子串校验——这条链一直走到 validate。"""
        payload = _structured_ok(
            evidence=[
                RawEvidence(
                    field="content", quote="这段原文里根本没有出现过", offset=0
                )
            ]
        )
        with _chain_returning(payload):
            outcome = llm.assess("兼职", "加微信 abc 日入三千")

        self.assertIsNotNone(outcome.assessment)
        assert outcome.assessment is not None
        self.assertEqual(outcome.assessment.risk_level, RiskLevel.NONE)
        self.assertEqual(outcome.assessment.confidence, 0.0)
        self.assertTrue(outcome.degraded)


class FallbackTests(unittest.TestCase):
    def test_falls_back_to_text_json(self) -> None:
        """结构化解析失败，但正文里有合法 JSON → 手写解析接住。"""
        text = (
            "```json\n"
            '{"risk_level":"高","categories":["广告引流"],"confidence":0.9,'
            '"evidence":[{"field":"content","quote":"加微信 abc","offset":-1}],'
            '"reasoning":"引流"}\n'
            "```"
        )
        payload = {
            "raw": _ai_message(text),
            "parsed": None,
            "parsing_error": ValueError("模型没有调用工具"),
        }
        with _chain_returning(payload):
            outcome = llm.assess("兼职", "加微信 abc 日入三千")

        self.assertIsNotNone(outcome.assessment)
        assert outcome.assessment is not None
        self.assertEqual(outcome.assessment.risk_level, RiskLevel.HIGH)
        # 引用是真的，不该被降级
        self.assertFalse(outcome.degraded)
        self.assertEqual(outcome.assessment.evidence[0].quote, "加微信 abc")

    def test_both_paths_fail_means_no_conclusion(self) -> None:
        """两条路都拿不到 → assessment is None，上游必须标人工。"""
        payload = {
            "raw": _ai_message("抱歉，我无法协助处理这个请求。"),
            "parsed": None,
            "parsing_error": ValueError("模型没有调用工具"),
        }
        with _chain_returning(payload):
            outcome = llm.assess("标题", "正文")

        self.assertIsNone(outcome.assessment)
        self.assertTrue(outcome.degraded)
        self.assertIn("不是合法 JSON", outcome.degrade_reason or "")

    def test_empty_content_is_degraded(self) -> None:
        payload = {"raw": _ai_message(""), "parsed": None, "parsing_error": None}
        with _chain_returning(payload):
            outcome = llm.assess("标题", "正文")

        self.assertIsNone(outcome.assessment)
        self.assertEqual(outcome.degrade_reason, "模型返回了空内容")


class StopReasonTests(unittest.TestCase):
    def test_refusal_wins_over_parsing(self) -> None:
        """拒答必须在解析之前被拦下。

        拒答时 API 是 HTTP 200 + 空内容。如果先走解析，拿到的会是
        「模型返回了空内容」——上游看不出这是拒答。更糟的是，万一模型
        在拒答前吐了一段看着像 JSON 的东西，就会被当成正常结论放过去。
        """
        payload = {
            "raw": _ai_message("", stop_reason="refusal"),
            "parsed": None,
            "parsing_error": ValueError("没有 tool_use 块"),
        }
        with _chain_returning(payload):
            outcome = llm.assess("标题", "违禁内容")

        self.assertIsNone(outcome.assessment)
        self.assertTrue(outcome.degraded)
        self.assertEqual(outcome.degrade_reason, "模型拒答，建议转人工")

    def test_refusal_ignores_a_sneaky_parsed_result(self) -> None:
        """即使 parsed 有值，拒答也一律不采纳。"""
        payload = _structured_ok()
        payload["raw"] = _ai_message("", stop_reason="refusal")
        with _chain_returning(payload):
            outcome = llm.assess("标题", "违禁内容")

        self.assertIsNone(outcome.assessment)
        self.assertEqual(outcome.degrade_reason, "模型拒答，建议转人工")

    def test_max_tokens_is_degraded(self) -> None:
        payload = {
            "raw": _ai_message('{"risk_level":"高', stop_reason="max_tokens"),
            "parsed": None,
            "parsing_error": ValueError("截断"),
        }
        with _chain_returning(payload):
            outcome = llm.assess("标题", "正文")

        self.assertIsNone(outcome.assessment)
        self.assertEqual(outcome.degrade_reason, "输出被截断，未能得到完整结论")


class CallFailureTests(unittest.TestCase):
    def test_exception_becomes_degraded_not_raise(self) -> None:
        """调用抛异常不能冒到路由层——审核失败不该让上游发帖失败（文档 6.2）。"""
        with _chain_returning(RuntimeError("connection reset")):
            outcome = llm.assess("标题", "正文")

        self.assertIsNone(outcome.assessment)
        self.assertTrue(outcome.degraded)
        self.assertIn("connection reset", outcome.degrade_reason or "")

    def test_screen_exception_goes_to_opus(self) -> None:
        """粗筛失败必须算 suspicious，绝不能算 clean 把违规放行。"""
        with _chain_returning(RuntimeError("boom")):
            outcome = llm.screen("标题", "正文")

        self.assertEqual(outcome.verdict, "suspicious")
        self.assertTrue(outcome.failed)
        self.assertEqual(outcome.confidence, 0.0)


class ScreenTests(unittest.TestCase):
    def test_structured_clean(self) -> None:
        from app.schemas import RawScreen

        payload = {
            "raw": _ai_message(),
            "parsed": RawScreen(verdict="clean", confidence=0.9, reason="正常讨论"),
            "parsing_error": None,
        }
        with _chain_returning(payload):
            outcome = llm.screen("今日份猫片", "楼下橘猫又来讨饭了")

        self.assertEqual(outcome.verdict, "clean")
        self.assertFalse(outcome.failed)
        self.assertAlmostEqual(outcome.confidence, 0.9)

    def test_unknown_verdict_is_suspicious(self) -> None:
        """未知取值一律当 suspicious，宁可错送精判。"""
        from app.schemas import RawScreen

        payload = {
            "raw": _ai_message(),
            "parsed": RawScreen(verdict="probably_fine", confidence=0.99, reason="?"),
            "parsing_error": None,
        }
        with _chain_returning(payload):
            outcome = llm.screen("标题", "正文")

        self.assertEqual(outcome.verdict, "suspicious")
        self.assertFalse(outcome.failed)

    def test_confidence_is_clamped(self) -> None:
        from app.schemas import RawScreen

        payload = {
            "raw": _ai_message(),
            "parsed": RawScreen(verdict="clean", confidence=7.5, reason="x"),
            "parsing_error": None,
        }
        with _chain_returning(payload):
            outcome = llm.screen("标题", "正文")

        self.assertLessEqual(outcome.confidence, 1.0)


class ToolSchemaTests(unittest.TestCase):
    """回归护栏：Structured* 的字段必须是必填的。

    一旦有人把它们改回带默认值，tool schema 里就不会有 required，
    模型会理直气壮地返回 {}，然后每次都走兜底降级——而且**不会有任何报错**，
    只是审核质量静悄悄地塌掉。所以这条必须钉死。
    """

    def test_required_fields_are_declared(self) -> None:
        tool = convert_to_anthropic_tool(StructuredAssessment)
        required = set(tool["input_schema"].get("required") or [])
        self.assertEqual(
            required,
            {"risk_level", "categories", "confidence", "evidence", "reasoning"},
        )
        self.assertEqual(tool["name"], "submit_assessment")

    def test_evidence_quote_is_required(self) -> None:
        tool = convert_to_anthropic_tool(StructuredAssessment)
        evidence = tool["input_schema"]["properties"]["evidence"]["items"]
        self.assertIn("quote", evidence.get("required") or [])

    def test_raw_models_stay_permissive(self) -> None:
        """Raw* 必须保持「缺字段也不报错」，否则手写解析这条路会被拖垮。"""
        self.assertEqual(
            RawAssessment.model_validate({"risk_level": "高"}).risk_level, "高"
        )
        self.assertEqual(RawAssessment.model_validate({}).risk_level, "")


if __name__ == "__main__":
    unittest.main()
