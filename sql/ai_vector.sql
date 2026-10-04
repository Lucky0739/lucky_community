-- AI 向量表：与业务表同一个 PostgreSQL 实例、同一个 community schema。
-- 开发环境密码需与 AI_DATABASE_URL 保持一致；生产环境应在部署时替换。
CREATE EXTENSION IF NOT EXISTS vector;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_agent') THEN
        CREATE ROLE ai_agent LOGIN PASSWORD 'ai_agent_dev';
    END IF;
END
$$;

CREATE SCHEMA IF NOT EXISTS community;
GRANT CONNECT ON DATABASE community TO ai_agent;
GRANT USAGE ON SCHEMA public TO ai_agent;
GRANT USAGE, CREATE ON SCHEMA community TO ai_agent;

CREATE TABLE IF NOT EXISTS community.post_vectors (
    post_id bigint NOT NULL,
    bar_id bigint NOT NULL,
    chunk_index integer NOT NULL,
    title text NOT NULL,
    content text NOT NULL,
    historical_action varchar(32) NOT NULL DEFAULT '未处置',
    embedding vector(384) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_post_vectors_bar
    ON community.post_vectors (bar_id, post_id);
CREATE INDEX IF NOT EXISTS idx_post_vectors_search
    ON community.post_vectors USING gin (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''))
    );

ALTER TABLE community.post_vectors OWNER TO ai_agent;
GRANT SELECT, INSERT, UPDATE, DELETE ON community.post_vectors TO ai_agent;
