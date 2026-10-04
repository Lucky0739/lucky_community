"""AI 自有 pgvector 存储。

表在 ``community`` schema，保存向量及检索所需的最小文本副本。
"""

from __future__ import annotations

import hashlib
import math
from collections import defaultdict

import httpx
import psycopg

from ..config import settings
from ..schemas import RetrievalResult, SimilarPost


def _chunks(title: str, content: str, size: int = 500) -> list[str]:
    values = [title.strip()] if title.strip() else []
    body = content.strip()
    values.extend(body[i : i + size] for i in range(0, len(body), size))
    return values or [""]


def embed(text: str) -> list[float]:
    """优先调用 OpenAI 兼容 Embedding；未配置时使用稳定的本地字符向量。"""
    if settings.embedding_base_url:
        headers = (
            {"Authorization": f"Bearer {settings.embedding_api_key}"}
            if settings.embedding_api_key
            else {}
        )
        response = httpx.post(
            f"{settings.embedding_base_url.rstrip('/')}/embeddings",
            headers=headers,
            json={"model": settings.embedding_model, "input": text},
            timeout=1.5,
        )
        response.raise_for_status()
        vector = response.json()["data"][0]["embedding"]
        if len(vector) != settings.embedding_dimensions:
            raise ValueError(
                f"Embedding 维度为 {len(vector)}，配置要求 {settings.embedding_dimensions}"
            )
        return [float(value) for value in vector]

    dimensions = settings.embedding_dimensions
    vector = [0.0] * dimensions
    normalized = " ".join(text.lower().split())
    grams = [normalized[i : i + 3] for i in range(max(1, len(normalized) - 2))]
    if not grams:
        grams = [normalized]
    for gram in grams:
        digest = hashlib.blake2b(gram.encode("utf-8"), digest_size=8).digest()
        value = int.from_bytes(digest, "big")
        index = value % dimensions
        vector[index] += -1.0 if value & 1 else 1.0
    norm = math.sqrt(sum(item * item for item in vector)) or 1.0
    return [item / norm for item in vector]


def _literal(vector: list[float]) -> str:
    return "[" + ",".join(f"{value:.8f}" for value in vector) + "]"


def ensure_schema() -> None:
    dimensions = settings.embedding_dimensions
    if not 16 <= dimensions <= 4096:
        raise ValueError("AI_EMBEDDING_DIMENSIONS 必须在 16 到 4096 之间")
    ddl = f"""
        CREATE TABLE IF NOT EXISTS community.post_vectors (
            post_id bigint NOT NULL,
            bar_id bigint NOT NULL,
            chunk_index integer NOT NULL,
            title text NOT NULL,
            content text NOT NULL,
            historical_action varchar(32) NOT NULL DEFAULT '未处置',
            embedding vector({dimensions}) NOT NULL,
            updated_at timestamptz NOT NULL DEFAULT now(),
            PRIMARY KEY (post_id, chunk_index)
        );
        CREATE INDEX IF NOT EXISTS idx_post_vectors_bar
            ON community.post_vectors (bar_id, post_id);
        CREATE INDEX IF NOT EXISTS idx_post_vectors_search
            ON community.post_vectors USING gin (
                to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''))
            );
    """
    with psycopg.connect(settings.database_url) as conn:
        conn.execute(ddl)


def upsert_post(post_id: int, bar_id: int, title: str, content: str) -> None:
    chunks = _chunks(title, content)
    with psycopg.connect(settings.database_url) as conn:
        conn.execute("DELETE FROM community.post_vectors WHERE post_id = %s", (post_id,))
        for index, chunk in enumerate(chunks):
            conn.execute(
                """
                INSERT INTO community.post_vectors
                    (post_id, bar_id, chunk_index, title, content, embedding)
                VALUES (%s, %s, %s, %s, %s, %s::vector)
                """,
                (post_id, bar_id, index, title, chunk, _literal(embed(chunk))),
            )


def delete_post(post_id: int) -> None:
    with psycopg.connect(settings.database_url) as conn:
        conn.execute("DELETE FROM community.post_vectors WHERE post_id = %s", (post_id,))


def update_action(post_id: int, action: str) -> None:
    with psycopg.connect(settings.database_url) as conn:
        conn.execute(
            "UPDATE community.post_vectors SET historical_action = %s WHERE post_id = %s",
            (action, post_id),
        )


def _rrf(
    vector_rows: list[tuple], keyword_rows: list[tuple], k: int
) -> list[SimilarPost]:
    scores: defaultdict[int, float] = defaultdict(float)
    rows: dict[int, tuple] = {}
    for ranking in (vector_rows, keyword_rows):
        for rank, row in enumerate(ranking, start=1):
            post_id = int(row[0])
            scores[post_id] += 1.0 / (60 + rank)
            rows[post_id] = row
    ordered = sorted(scores, key=scores.get, reverse=True)[:k]
    max_score = max((scores[post_id] for post_id in ordered), default=1.0)
    return [
        SimilarPost(
            post_id=post_id,
            title=str(rows[post_id][1]),
            snippet=str(rows[post_id][2])[:160],
            score=round(scores[post_id] / max_score, 4),
            historical_action=str(rows[post_id][3] or "未处置"),
        )
        for post_id in ordered
    ]


def search(bar_id: int, query: str, exclude_post_id: int, k: int | None = None) -> RetrievalResult:
    limit = k or settings.retrieval_k
    query_vector = _literal(embed(query))
    with psycopg.connect(settings.database_url) as conn:
        vector_rows = conn.execute(
            """
            SELECT post_id, max(title), max(content), max(historical_action),
                   min(embedding <=> %s::vector) AS distance
            FROM community.post_vectors
            WHERE bar_id = %s AND post_id <> %s
            GROUP BY post_id
            ORDER BY distance
            LIMIT %s
            """,
            (query_vector, bar_id, exclude_post_id, limit),
        ).fetchall()
        keyword_rows = conn.execute(
            """
            SELECT post_id, max(title), max(content), max(historical_action),
                   max(ts_rank_cd(
                       to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, '')),
                       plainto_tsquery('simple', %s)
                   )) AS rank
            FROM community.post_vectors
            WHERE bar_id = %s AND post_id <> %s
              AND to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''))
                  @@ plainto_tsquery('simple', %s)
            GROUP BY post_id
            ORDER BY rank DESC
            LIMIT %s
            """,
            (query, bar_id, exclude_post_id, query, limit),
        ).fetchall()
    return RetrievalResult(similar_posts=_rrf(vector_rows, keyword_rows, limit))
