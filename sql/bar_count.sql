-- 贴吧粉丝数计数表（PostgreSQL，schema：community）
--
-- tb_follow 存关注明细；本表存每个吧的粉丝数，避免列表上对 tb_follow 做 COUNT(*)。
-- 关注关系落库（FollowPersistConsumer）时对 fan_count 加减。
--
-- 执行顺序：先 bar.sql、follow.sql，再本文件。已有库可单独执行。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/bar_count.sql

CREATE SCHEMA IF NOT EXISTS community;

CREATE OR REPLACE FUNCTION community.touch_update_time()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS community.tb_bar_count (
    bar_id      bigint    NOT NULL,                           -- 贴吧ID，主键，指向 tb_bar.id
    fan_count   integer   NOT NULL DEFAULT 0,                 -- 粉丝数，与 tb_follow 对账
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bar_id)
);
COMMENT ON TABLE community.tb_bar_count IS '贴吧粉丝数计数表';
COMMENT ON COLUMN community.tb_bar_count.bar_id IS '贴吧ID，主键，指向 tb_bar.id';
COMMENT ON COLUMN community.tb_bar_count.fan_count IS '粉丝数：关注该吧的用户数';

DROP TRIGGER IF EXISTS trg_tb_bar_count_touch ON community.tb_bar_count;
CREATE TRIGGER trg_tb_bar_count_touch
    BEFORE UPDATE ON community.tb_bar_count
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

-- 为已有贴吧补行，并按 tb_follow 回填粉丝数
INSERT INTO community.tb_bar_count (bar_id, fan_count)
SELECT b.id,
       COALESCE((
           SELECT COUNT(*)::integer FROM community.tb_follow f WHERE f.bar_id = b.id
       ), 0)
FROM community.tb_bar b
ON CONFLICT (bar_id) DO UPDATE
    SET fan_count = EXCLUDED.fan_count,
        update_time = CURRENT_TIMESTAMP;

-- 可选：去掉 tb_bar 上的冗余列（若存在）
ALTER TABLE community.tb_bar DROP COLUMN IF EXISTS follow_count;
