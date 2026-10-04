-- 收藏域建表脚本（PostgreSQL，schema：community）
--
-- 收藏是低频写，直接落本表。
--
-- 执行顺序：先 user.sql、bar.sql、post.sql，再本文件。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f favorite.sql

CREATE SCHEMA IF NOT EXISTS community;

CREATE TABLE community.tb_favorite (
    id          bigint    NOT NULL,                           -- 主键，雪花ID
    user_id     bigint    NOT NULL,                           -- 收藏用户ID
    post_id     bigint    NOT NULL,                           -- 被收藏的帖子ID
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 收藏时间
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_favorite IS '帖子收藏表';
COMMENT ON COLUMN community.tb_favorite.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_favorite.user_id IS '收藏用户ID';
COMMENT ON COLUMN community.tb_favorite.post_id IS '被收藏的帖子ID';
COMMENT ON COLUMN community.tb_favorite.create_time IS '收藏时间';
CREATE UNIQUE INDEX uk_favorite_user_post ON community.tb_favorite (user_id, post_id);
CREATE INDEX idx_favorite_user_id_id ON community.tb_favorite (user_id, id);
CREATE INDEX idx_favorite_post_id ON community.tb_favorite (post_id);
