from __future__ import annotations

import unittest
from unittest.mock import patch

from app.agents import extraction, recheck
from app.moderation import prompt_store, prompts
from app.moderation.prompt_store import PromptValue
from app.schemas import Conflict, RiskAssessment, RiskLevel


class PromptStoreTests(unittest.TestCase):
    def setUp(self) -> None:
        prompt_store._local.clear()

    def test_postgresql_result_is_cached_in_redis_and_memory(self) -> None:
        value = PromptValue("test.prompt", 3, "数据库内容", "checksum", "postgresql")
        with (
            patch.object(prompt_store, "_from_redis", return_value=None),
            patch.object(prompt_store, "_from_database", return_value=value),
            patch.object(prompt_store, "_write_redis") as write_redis,
        ):
            result = prompt_store.get_prompt("test.prompt", "默认内容")

        self.assertEqual(result, value)
        self.assertEqual(prompt_store._local["test.prompt"], value)
        write_redis.assert_called_once_with(value)

    def test_last_successful_value_is_used_when_stores_fail(self) -> None:
        prompt_store._local["test.prompt"] = PromptValue(
            "test.prompt", 2, "旧内容", "checksum", "postgresql"
        )
        with (
            patch.object(prompt_store, "_from_redis", return_value=None),
            patch.object(prompt_store, "_from_database", return_value=None),
        ):
            result = prompt_store.get_prompt("test.prompt", "默认内容")

        self.assertEqual(result.content, "旧内容")
        self.assertEqual(result.version, 2)
        self.assertEqual(result.source, "memory")

    def test_code_default_is_final_fallback(self) -> None:
        with (
            patch.object(prompt_store, "_from_redis", return_value=None),
            patch.object(prompt_store, "_from_database", return_value=None),
        ):
            result = prompt_store.get_prompt("test.prompt", "默认内容")

        self.assertEqual(result.content, "默认内容")
        self.assertEqual(result.version, 0)
        self.assertEqual(result.source, "code-fallback")
        self.assertEqual(result.checksum, prompt_store._checksum("默认内容"))


class PromptRenderingTests(unittest.TestCase):
    def test_judge_schema_placeholder_is_rendered(self) -> None:
        stored = PromptValue(
            prompts.SYSTEM_PROMPT_KEY,
            4,
            "schema={{schema_text}}",
            "checksum",
            "postgresql",
        )
        with patch.object(prompts, "get_prompt", return_value=stored):
            result = prompts.load_judge_system_prompt()

        self.assertNotIn("{{schema_text}}", result.content)
        self.assertIn('"risk_level"', result.content)
        self.assertEqual(result.version, 4)

    def test_user_variables_are_rendered_without_format_parsing(self) -> None:
        stored = PromptValue(
            prompts.USER_PROMPT_KEY,
            1,
            "标题={{title}}；正文={{content}}",
            "checksum",
            "redis",
        )
        with patch.object(prompts, "get_prompt", return_value=stored):
            result = prompts.load_user_prompt("{标题}", '{"x": 1}')

        self.assertEqual(result.content, '标题={标题}；正文={"x": 1}')


class _FakeChain:
    def __init__(self, parsed: object) -> None:
        self.parsed = parsed
        self.metadata: dict = {}
        self.messages: list = []

    def with_config(self, **config):
        self.metadata = config.get("metadata") or {}
        return self

    def invoke(self, messages):
        self.messages = messages
        return {"parsed": self.parsed, "raw": None}


class _FakeModel:
    def __init__(self, chain: _FakeChain) -> None:
        self.chain = chain

    def with_structured_output(self, *args, **kwargs):
        return self.chain


class AgentPromptTests(unittest.TestCase):
    @staticmethod
    def _prompt(key: str, content: str) -> PromptValue:
        return PromptValue(key, 7, content, "checksum", "postgresql")

    def test_extraction_uses_stored_system_and_user_prompts(self) -> None:
        parsed = extraction.PostExtraction(
            title="标题",
            summary="摘要",
            entities=[],
            topics=[],
            content_type="分享",
            language="zh",
        )
        chain = _FakeChain(parsed)

        def load(key: str, _fallback: str) -> PromptValue:
            content = (
                "数据库抽取系统词"
                if key == extraction._SYSTEM_KEY
                else "{{title}}|{{content}}|{{image_urls}}"
            )
            return self._prompt(key, content)

        with (
            patch.object(extraction, "get_model", return_value=_FakeModel(chain)),
            patch.object(extraction, "get_prompt", side_effect=load),
        ):
            outcome = extraction.extract("题", "正文", ["https://img"])

        self.assertFalse(outcome.degraded)
        self.assertEqual(chain.messages[0].content, "数据库抽取系统词")
        self.assertEqual(chain.messages[1].content, "题|正文|https://img")
        self.assertEqual(chain.metadata["system_prompt_version"], 7)

    def test_recheck_uses_stored_system_and_user_prompts(self) -> None:
        assessment = RiskAssessment(
            risk_level=RiskLevel.NONE,
            categories=[],
            confidence=0,
            evidence=[],
            reasoning="无风险",
        )
        chain = _FakeChain(assessment)

        def load(key: str, _fallback: str) -> PromptValue:
            content = (
                "数据库复核系统词"
                if key == recheck._SYSTEM_KEY
                else "{{title}}|{{content}}|{{current}}|{{conflicts}}"
            )
            return self._prompt(key, content)

        conflict = Conflict(
            type="test",
            claim="冲突",
            question="是否维持？",
        )
        with (
            patch.object(recheck, "get_model", return_value=_FakeModel(chain)),
            patch.object(recheck, "get_prompt", side_effect=load),
        ):
            outcome = recheck.recheck("题", "正文", [conflict], assessment)

        self.assertIsNotNone(outcome.assessment)
        self.assertEqual(chain.messages[0].content, "数据库复核系统词")
        self.assertNotIn("{{current}}", chain.messages[1].content)
        self.assertNotIn("{{conflicts}}", chain.messages[1].content)
        self.assertEqual(chain.metadata["user_prompt_version"], 7)


if __name__ == "__main__":
    unittest.main()
