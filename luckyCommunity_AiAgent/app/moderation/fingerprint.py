"""漏斗第 ② 层：内容指纹缓存，复用历史审核结论。

走项目已有 Redis，不引新中间件。两级查找：

1. 原文 SHA256 —— 同一正文再发，直接命中
2. SimHash LSH（4 段 16-bit 分桶）—— 话术相同、微信号/链接不同的刷屏广告

近邻复用只接受「有风险」结论。无风险不能按相似套用：多一句违禁词
海明距离仍可能很低，那是漏放而不是省钱。

精确哈希两向都复用：同一正文再发一次，结论就是那次的。
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass

from redis import Redis
from redis.exceptions import RedisError

from ..config import settings
from ..schemas import RiskAssessment, RiskLevel
from . import simhash

logger = logging.getLogger(__name__)

# 与 Java RedisKeyManage 对齐，避免各写各的撞 key。
_EXACT_KEY = "moderation:fp:exact:{digest}"
_SIM_KEY = "moderation:fp:sim:{fp}"
_BAND_KEY = "moderation:fp:band:{band}:{value}"

_redis: Redis | None = None
_redis_failed_at = 0.0
_REDIS_RETRY_SECONDS = 30.0


@dataclass(frozen=True)
class FingerprintHit:
    assessment: RiskAssessment
    model_version: str
    match: str  # exact | simhash


def _client() -> Redis | None:
    """懒连接。Redis 挂了就跳过本层，审核不能因为缓存不可用而失败。"""
    global _redis, _redis_failed_at
    if _redis is not None:
        return _redis
    if _redis_failed_at and (time.monotonic() - _redis_failed_at) < _REDIS_RETRY_SECONDS:
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
        return _redis
    except Exception as e:  # noqa: BLE001 —— 缓存失败必须降级，不能抛到审核主路径
        _redis_failed_at = time.monotonic()
        logger.warning("审核指纹缓存 Redis 不可用，跳过第 ② 层：%s", e)
        return None


def _loads(raw: str) -> tuple[RiskAssessment, str] | None:
    try:
        payload = json.loads(raw)
        assessment = RiskAssessment.model_validate(payload["assessment"])
        model_version = str(payload.get("model_version") or "fingerprint-cache")
        return assessment, model_version
    except (KeyError, TypeError, ValueError) as e:
        logger.warning("指纹缓存条目损坏，已忽略：%s", e)
        return None


def _dumps(assessment: RiskAssessment, model_version: str) -> str:
    return json.dumps(
        {
            "assessment": assessment.model_dump(mode="json"),
            "model_version": model_version,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )


def lookup(title: str, content: str) -> FingerprintHit | None:
    client = _client()
    if client is None:
        return None
    normalized = simhash.normalize(title, content)
    if not normalized:
        return None
    try:
        # 精确键用原文，避免「只改了微信号」被当成同一帖、却带着旧引用
        exact = client.get(
            _EXACT_KEY.format(digest=simhash.exact_digest(f"{title}\n{content}"))
        )
        if isinstance(exact, str):
            parsed = _loads(exact)
            if parsed is not None:
                assessment, model_version = parsed
                return FingerprintHit(assessment, model_version, "exact")

        fingerprint = simhash.simhash64(normalized)
        candidates: set[str] = set()
        for band, value in simhash.bands(fingerprint):
            members = client.smembers(_BAND_KEY.format(band=band, value=value))
            if members:
                candidates.update(members)

        best: FingerprintHit | None = None
        best_distance = settings.simhash_max_hamming + 1
        for hex_fp in candidates:
            try:
                other = simhash.from_hex(hex_fp)
            except ValueError:
                continue
            distance = simhash.hamming(fingerprint, other)
            if distance > settings.simhash_max_hamming:
                continue
            raw = client.get(_SIM_KEY.format(fp=hex_fp))
            if not isinstance(raw, str):
                continue
            parsed = _loads(raw)
            if parsed is None:
                continue
            assessment, model_version = parsed
            # 近邻只复用有风险结论——见模块注释
            if assessment.risk_level is RiskLevel.NONE:
                continue
            if distance < best_distance:
                best_distance = distance
                best = FingerprintHit(assessment, model_version, "simhash")
        return best
    except RedisError as e:
        logger.warning("读取审核指纹缓存失败：%s", e)
        return None


def remember(title: str, content: str, assessment: RiskAssessment, model_version: str) -> None:
    """写入精确哈希；有风险的再进 SimHash 分桶，供模板广告近邻命中。"""
    client = _client()
    if client is None:
        return
    normalized = simhash.normalize(title, content)
    if not normalized:
        return
    payload = _dumps(assessment, model_version)
    ttl = settings.fingerprint_ttl_seconds
    fingerprint = simhash.simhash64(normalized)
    hex_fp = simhash.to_hex(fingerprint)
    try:
        pipe = client.pipeline(transaction=False)
        pipe.set(
            _EXACT_KEY.format(digest=simhash.exact_digest(f"{title}\n{content}")),
            payload,
            ex=ttl,
        )
        if assessment.risk_level is not RiskLevel.NONE:
            pipe.set(_SIM_KEY.format(fp=hex_fp), payload, ex=ttl)
            for band, value in simhash.bands(fingerprint):
                key = _BAND_KEY.format(band=band, value=value)
                pipe.sadd(key, hex_fp)
                pipe.expire(key, ttl)
        pipe.execute()
    except RedisError as e:
        logger.warning("写入审核指纹缓存失败：%s", e)


def available() -> bool:
    return _client() is not None
