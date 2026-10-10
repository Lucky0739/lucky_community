-- 帖子点赞 / 收藏 / 评论计数（PostgreSQL，schema：community）
--
-- tb_thumb / tb_favorite / tb_comment 存明细。本表存每帖计数，避免列表 COUNT(*)。
-- 热路径可以 Redis 累加，再刷进 like_count / favorite_count / comment_count。
--
-- 执行顺序：先 post.sql，再本文件。已有库可单独执行。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/post_count.sql

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

CREATE TABLE community.tb_post_count (
    post_id         bigint    NOT NULL,                           -- 帖子ID，主键，指向 tb_post.id
    like_count      bigint    NOT NULL DEFAULT 0,                 -- 点赞数，与 tb_thumb 对账
    favorite_count  bigint    NOT NULL DEFAULT 0,                 -- 收藏数，与 tb_favorite 对账
    comment_count   bigint    NOT NULL DEFAULT 0,                 -- 评论数，与 tb_comment 对账
    create_time     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (post_id)
);
COMMENT ON TABLE community.tb_post_count IS '帖子点赞、收藏与评论计数表';
COMMENT ON COLUMN community.tb_post_count.post_id IS '帖子ID，主键，指向 tb_post.id';
COMMENT ON COLUMN community.tb_post_count.like_count IS '点赞数。热路径可走 Redis，本列是落盘值';
COMMENT ON COLUMN community.tb_post_count.favorite_count IS '收藏数。热路径可走 Redis，本列是落盘值';
COMMENT ON COLUMN community.tb_post_count.comment_count IS '评论数。热路径可走 Redis，本列是落盘值';
COMMENT ON COLUMN community.tb_post_count.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_post_count.update_time IS '更新时间，由触发器维护';
CREATE TRIGGER trg_tb_post_count_touch
    BEFORE UPDATE ON community.tb_post_count
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();
