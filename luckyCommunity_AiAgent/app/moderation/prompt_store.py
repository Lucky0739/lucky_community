"""提示词仓库：Redis 缓存，PostgreSQL 权威存储，进程内副本兜底。"""

from __future__ import annotations

import hashlib
import json
import logging
import time
from dataclasses import dataclass

import psycopg
from redis import Redis
from redis.exceptions import RedisError

from ..config import settings

logger = logging.getLogger(__name__)

_ACTIVE_KEY = "ai:prompt:active:{prompt_key}"
_VERSION_KEY = "ai:prompt:{prompt_key}:{version}"
_RETRY_SECONDS = 30.0

_redis: Redis | None = None
_redis_failed_at = 0.0
_database_failed_at = 0.0
_local: dict[str, "PromptValue"] = {}


@dataclass(frozen=True)
class PromptValue:
    prompt_key: str
    version: int
    content: str
    checksum: str
    source: str


def _checksum(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()

def _redis_client() -> Redis | None:
    global _redis, _redis_failed_at
    if _redis is not None:
        return _redis
    if _redis_failed_at and time.monotonic() - _redis_failed_at < _RETRY_SECONDS:
        return None
    try:
        client = Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password or None,
            decode_responses=True,
            socket_connect_timeout=0.2,
            socket_timeout=0.2,
        )
        client.ping()
        _redis = client
        _redis_failed_at = 0.0
        return client
    except Exception as exc:  # noqa: BLE001
        _redis_failed_at = time.monotonic()
        logger.warning("提示词 Redis 缓存不可用，将回源 PostgreSQL：%s", exc)
        return None


def _from_redis(prompt_key: str) -> PromptValue | None:
    client = _redis_client()
    if client is None:
        return None
    try:
        version = client.get(_ACTIVE_KEY.format(prompt_key=prompt_key))
        if not version:
            return None
        raw = client.get(_VERSION_KEY.format(prompt_key=prompt_key, version=version))
        if not raw:
            return None
        payload = json.loads(raw)
        content = str(payload["content"])
        checksum = str(payload["checksum"])
        if checksum != _checksum(content):
            logger.warning("提示词 Redis 缓存校验失败，已忽略：%s v%s", prompt_key, version)
            return None
        return PromptValue(prompt_key, int(version), content, checksum, "redis")
    except (RedisError, KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
        logger.warning("读取提示词 Redis 缓存失败，将回源 PostgreSQL：%s", exc)
        return None


def _from_database(prompt_key: str) -> PromptValue | None:
    global _database_failed_at
    if _database_failed_at and time.monotonic() - _database_failed_at < _RETRY_SECONDS:
        return None
    try:
        with psycopg.connect(settings.database_url, connect_timeout=1) as conn:
            row = conn.execute(
                """
                SELECT version, content, checksum
                FROM community.prompt_templates
                WHERE prompt_key = %s AND status = 'published'
                ORDER BY version DESC
                LIMIT 1
                """,
                (prompt_key,),
            ).fetchone()
        _database_failed_at = 0.0
    except Exception as exc:  # noqa: BLE001
        _database_failed_at = time.monotonic()
        logger.warning("提示词 PostgreSQL 不可用，将使用进程内版本：%s", exc)
        return None
    if row is None:
        logger.warning("PostgreSQL 中没有已发布提示词：%s", prompt_key)
        return None
    version, content, checksum = int(row[0]), str(row[1]), str(row[2])
    if checksum != _checksum(content):
        logger.error("提示词 PostgreSQL 校验失败，已拒绝加载：%s v%s", prompt_key, version)
        return None
    return PromptValue(prompt_key, version, content, checksum, "postgresql")


def _write_redis(value: PromptValue) -> None:
    client = _redis_client()
    if client is None:
        return
    version_key = _VERSION_KEY.format(
        prompt_key=value.prompt_key, version=value.version
    )
    active_key = _ACTIVE_KEY.format(prompt_key=value.prompt_key)
    payload = json.dumps(
        {"content": value.content, "checksum": value.checksum},
        ensure_ascii=False,
        separators=(",", ":"),
    )
    try:
        pipe = client.pipeline(transaction=False)
        pipe.set(version_key, payload, ex=settings.prompt_cache_ttl_seconds)
        pipe.set(active_key, value.version, ex=settings.prompt_cache_ttl_seconds)
        pipe.execute()
    except RedisError as exc:
        logger.warning("回填提示词 Redis 缓存失败：%s", exc)


def get_prompt(prompt_key: str, fallback: str) -> PromptValue:
    """按 Redis → PostgreSQL → 进程内旧版本 → 代码默认值读取提示词。"""
    value = _from_redis(prompt_key)
    if value is None:
        value = _from_database(prompt_key)
        if value is not None:
            _write_redis(value)
    if value is not None:
        _local[prompt_key] = value
        return value
    cached = _local.get(prompt_key)
    if cached is not None:
        return PromptValue(
            cached.prompt_key,
            cached.version,
            cached.content,
            cached.checksum,
            "memory",
        )
    value = PromptValue(prompt_key, 0, fallback, _checksum(fallback), "code-fallback")
    _local[prompt_key] = value
    return value
