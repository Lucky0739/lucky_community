-- 点赞域建表脚本（PostgreSQL，schema：community）
--
-- 热路径在 Redis，本表是落盘和对账依据。取消点赞物理删除，才能释放唯一约束。
--
-- 执行顺序：先 user.sql、bar.sql、post.sql，再本文件。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/thumb.sql

CREATE SCHEMA IF NOT EXISTS community;

CREATE TABLE community.tb_thumb (
    id          bigint    NOT NULL,                           -- 主键，雪花ID
    user_id     bigint    NOT NULL,                           -- 点赞用户ID
    post_id     bigint    NOT NULL,                           -- 被点赞的帖子ID
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 点赞时间。取消点赞时物理删除本行。热路径在 Redis，本表用于落盘对账
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_thumb IS '帖子点赞表';
COMMENT ON COLUMN community.tb_thumb.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_thumb.user_id IS '点赞用户ID';
COMMENT ON COLUMN community.tb_thumb.post_id IS '被点赞的帖子ID';
COMMENT ON COLUMN community.tb_thumb.create_time IS '点赞时间。取消点赞时物理删除本行';
CREATE UNIQUE INDEX uk_thumb_user_post ON community.tb_thumb (user_id, post_id);
CREATE INDEX idx_thumb_post_id ON community.tb_thumb (post_id);
