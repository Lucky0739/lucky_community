-- 容器首次初始化时执行。业务表不放这里，仍按 user.sql → bar.sql → post.sql → 其余脚本手工导入。
CREATE SCHEMA IF NOT EXISTS community;
ALTER DATABASE community SET search_path TO community, public;
CREATE EXTENSION IF NOT EXISTS vector;

-- AI 与业务表共用 community schema。ai_agent 只有本 schema 的 USAGE/CREATE，
-- 业务表不会默认授权；prompt.sql / ai_vector.sql 再授予那两张 AI 表。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_agent') THEN
        CREATE ROLE ai_agent LOGIN PASSWORD 'ai_agent_dev';
    END IF;
END
$$;
GRANT CONNECT ON DATABASE community TO ai_agent;
GRANT USAGE ON SCHEMA public TO ai_agent;
GRANT USAGE, CREATE ON SCHEMA community TO ai_agent;
