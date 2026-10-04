"""64-bit SimHash + 文本归一化。只依赖标准库。

引流机器人改的是微信号、链接、空格，不是整段话术。归一化先把 URL / 长数字 /
微信别称压成占位符，再算指纹，模板碰撞率才会高。
"""

from __future__ import annotations

import hashlib
import re

_URL_RE = re.compile(r"https?://\S+|www\.\S+", re.IGNORECASE)
# 微信号 / QQ / 订单号：纯数字或「字母+数字」都压成占位符，否则改一个账号指纹就散了
_ID_RE = re.compile(r"[a-z]*\d[a-z0-9]{2,}|\d{5,}", re.IGNORECASE)
_WX_RE = re.compile(r"微\s*信|v信|v[x×]|wx|威信|薇信", re.IGNORECASE)
_SPACE_RE = re.compile(r"\s+")


def normalize(title: str, content: str) -> str:
    """把模板广告压成可碰撞的骨架：去链接、去账号、统一微信写法。"""
    text = f"{title}\n{content}".casefold()
    text = _URL_RE.sub(" URL ", text)
    text = _WX_RE.sub(" 微信 ", text)
    text = _ID_RE.sub(" NUM ", text)
    return _SPACE_RE.sub(" ", text).strip()


def exact_digest(normalized: str) -> str:
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _features(text: str) -> list[str]:
    compact = _SPACE_RE.sub("", text)
    feats: list[str] = []
    for n in (2, 3):
        if len(compact) >= n:
            feats.extend(compact[i : i + n] for i in range(len(compact) - n + 1))
    feats.extend(w for w in text.split() if w.isascii() and len(w) > 1)
    return feats or [text or ""]


def simhash64(normalized: str) -> int:
    """Charikar SimHash，64 位。特征是字 bigram/trigram + ASCII 词。"""
    vector = [0] * 64
    for feat in _features(normalized):
        digest = hashlib.blake2b(feat.encode("utf-8"), digest_size=8).digest()
        h = int.from_bytes(digest, "big")
        for i in range(64):
            if (h >> i) & 1:
                vector[i] += 1
            else:
                vector[i] -= 1
    fingerprint = 0
    for i in range(64):
        if vector[i] >= 0:
            fingerprint |= 1 << i
    return fingerprint


def hamming(left: int, right: int) -> int:
    return (left ^ right).bit_count()


def to_hex(fingerprint: int) -> str:
    return f"{fingerprint:016x}"


def from_hex(value: str) -> int:
    return int(value, 16)


# 4 段 × 16 bit。任一段碰撞就进入候选，再用海明距离精滤。
_BANDS = 4
_BAND_BITS = 16
_BAND_MASK = (1 << _BAND_BITS) - 1


def bands(fingerprint: int) -> list[tuple[int, str]]:
    return [
        (i, f"{(fingerprint >> (i * _BAND_BITS)) & _BAND_MASK:04x}")
        for i in range(_BANDS)
    ]
