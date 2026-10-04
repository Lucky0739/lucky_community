"""运行期配置：优先读 ai.toml（自己填模型和密钥），环境变量可覆盖。

刻意不引 pydantic-settings：配置项不多，多一个依赖不值。
"""

from __future__ import annotations

import os
import tomllib
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# .env 缺失时不报错——生产环境用真实环境变量注入更常见
load_dotenv()

_AI_TOML = Path(__file__).resolve().parent.parent / "ai.toml"


def _load_ai_toml() -> dict:
    """读取 AiAgent 根目录的 ai.toml。[moderation] 段；文件不存在则当空配置。"""
    if not _AI_TOML.is_file():
        return {}
    with _AI_TOML.open("rb") as f:
        data = tomllib.load(f)
    section = data.get("moderation")
    return section if isinstance(section, dict) else {}


def _pick_str(*candidates: object, default: str) -> str:
    for value in candidates:
        if isinstance(value, str) and value.strip():
            return value.strip()
    return default

def _pick_int(*candidates: object, default: int) -> int:
    for value in candidates:
        if isinstance(value, int) and not isinstance(value, bool):
            return value
        if isinstance(value, str) and value.strip():
            return int(value.strip())
    return default


def _pick_float(*candidates: object, default: float) -> float:
    for value in candidates:
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            return float(value)
        if isinstance(value, str) and value.strip():
            return float(value.strip())
    return default


def _pick_optional_str(*candidates: object, default: str) -> str:
    """空字符串是有效值（用来关闭某项），None / 缺省才回落到 default。"""
    for value in candidates:
        if isinstance(value, str):
            return value.strip()
    return default


def _pick_optional_bool(*candidates: object) -> bool | None:
    """三态布尔：True / False / None（None = 调用方不要发送这个参数）。

    TOML 的 `thinking = false` 和「这个键压根没写」是两回事，前者要发
    `{"type":"disabled"}`，后者要什么都不发。所以不能像 _pick_str 那样
    回落到一个默认值，缺省必须原样保留成 None。
    """
    for value in candidates:
        if isinstance(value, bool):
            return value
        if isinstance(value, str) and value.strip():
            lowered = value.strip().lower()
            if lowered in {"true", "1", "yes", "on"}:
                return True
            if lowered in {"false", "0", "no", "off"}:
                return False
    return None


@dataclass(frozen=True)
class Settings:
    """审核服务配置。frozen 是为了防止运行期被就地改写。"""

    model: str
    screen_model: str
    api_key: str
    base_url: str
    structured_method: str
    thinking: bool | None
    max_tokens: int
    effort: str
    host: str
    port: int
    redis_host: str
    redis_port: int
    redis_password: str
    prompt_cache_ttl_seconds: int
    fingerprint_ttl_seconds: int
    simhash_max_hamming: int
    screen_timeout: int
    judge_timeout: int
    screen_accept_confidence: float
    database_url: str
    embedding_dimensions: int
    embedding_base_url: str
    embedding_api_key: str
    embedding_model: str
    retrieval_k: int

    @staticmethod
    def load() -> "Settings":
        file_cfg = _load_ai_toml()
        return Settings(
            # 精判默认 Opus。漏判代价高于每次调用省下的几分钱。
            model=_pick_str(
                os.getenv("MODERATION_MODEL"),
                file_cfg.get("model"),
                default="claude-opus-4-8",
            ),
            # 显式空字符串 = 跳过 Haiku 粗筛，直接精判。
            screen_model=_pick_optional_str(
                os.getenv("MODERATION_SCREEN_MODEL"),
                file_cfg.get("screen_model"),
                default="claude-haiku-4-5",
            ),
            api_key=_pick_str(
                os.getenv("ANTHROPIC_API_KEY"),
                file_cfg.get("api_key"),
                default="",
            ),
            # 空字符串 = 官方 api.anthropic.com。换任何 Anthropic 兼容端点
            # （如 DeepSeek 的 /anthropic）在这里填，不需要改代码。
            base_url=_pick_str(
                os.getenv("MODERATION_BASE_URL"),
                file_cfg.get("base_url"),
                default="",
            ),
            # 传给 with_structured_output(method=)。Anthropic 官方端点用
            # function_calling；DeepSeek 的兼容端点不支持强制 tool_choice
            # （thinking 模式下直接 400），必须用 json_schema。
            structured_method=_pick_str(
                os.getenv("MODERATION_STRUCTURED_METHOD"),
                file_cfg.get("structured_method"),
                default="json_schema",
            ),
            # 三态：未设置 = 不发 thinking 参数；false = 显式传 {"type":"disabled"}。
            thinking=_pick_optional_bool(
                os.getenv("MODERATION_THINKING"),
                file_cfg.get("thinking"),
            ),
            max_tokens=_pick_int(
                os.getenv("MODERATION_MAX_TOKENS"),
                file_cfg.get("max_tokens"),
                default=2048,
            ),
            effort=_pick_str(
                os.getenv("MODERATION_EFFORT"),
                file_cfg.get("effort"),
                default="low",
            ),
            host=_pick_str(os.getenv("AI_HOST"), default="127.0.0.1"),
            port=_pick_int(os.getenv("AI_PORT"), default=8000),
            # 与 docker-compose / Java 共用同一 Redis，不是新中间件。
            redis_host=_pick_str(
                os.getenv("REDIS_HOST"),
                file_cfg.get("redis_host"),
                default="127.0.0.1",
            ),
            redis_port=_pick_int(
                os.getenv("REDIS_PORT"),
                file_cfg.get("redis_port"),
                default=6380,
            ),
            redis_password=_pick_str(
                os.getenv("REDIS_PASSWORD"),
                file_cfg.get("redis_password"),
                default="",
            ),
            prompt_cache_ttl_seconds=_pick_int(
                os.getenv("AI_PROMPT_CACHE_TTL"),
                file_cfg.get("prompt_cache_ttl_seconds"),
                default=300,
            ),
            fingerprint_ttl_seconds=_pick_int(
                os.getenv("MODERATION_FP_TTL"),
                file_cfg.get("fingerprint_ttl_seconds"),
                default=7 * 24 * 3600,
            ),
            simhash_max_hamming=_pick_int(
                os.getenv("MODERATION_SIMHASH_HAMMING"),
                file_cfg.get("simhash_max_hamming"),
                default=3,
            ),
            screen_timeout=_pick_int(
                os.getenv("MODERATION_SCREEN_TIMEOUT"),
                file_cfg.get("screen_timeout"),
                default=2,
            ),
            # Java 读超时 8s；Haiku 2s + Opus 5s 最坏仍留余量。
            judge_timeout=_pick_int(
                os.getenv("MODERATION_JUDGE_TIMEOUT"),
                file_cfg.get("judge_timeout"),
                default=5,
            ),
            screen_accept_confidence=_pick_float(
                os.getenv("MODERATION_SCREEN_ACCEPT"),
                file_cfg.get("screen_accept_confidence"),
                default=0.75,
            ),
            # 两张 AI 表在 community schema。账号只应被授予这两张表，而不是全部业务表。
            database_url=_pick_str(
                os.getenv("AI_DATABASE_URL"),
                file_cfg.get("database_url"),
                default=(
                    "postgresql://ai_agent:ai_agent_dev@127.0.0.1:5432/community"
                    "?options=-csearch_path%3Dcommunity,public"
                ),
            ),
            embedding_dimensions=_pick_int(
                os.getenv("AI_EMBEDDING_DIMENSIONS"),
                file_cfg.get("embedding_dimensions"),
                default=384,
            ),
            embedding_base_url=_pick_optional_str(
                os.getenv("AI_EMBEDDING_BASE_URL"),
                file_cfg.get("embedding_base_url"),
                default="",
            ),
            embedding_api_key=_pick_optional_str(
                os.getenv("AI_EMBEDDING_API_KEY"),
                file_cfg.get("embedding_api_key"),
                default="",
            ),
            embedding_model=_pick_str(
                os.getenv("AI_EMBEDDING_MODEL"),
                file_cfg.get("embedding_model"),
                default="text-embedding-3-small",
            ),
            retrieval_k=_pick_int(
                os.getenv("AI_RETRIEVAL_K"),
                file_cfg.get("retrieval_k"),
                default=10,
            ),
        )


settings = Settings.load()
