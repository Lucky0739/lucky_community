-- 关注域建表脚本（PostgreSQL，schema：community）
--
-- 用户关注用户。取消关注物理删除，才能释放唯一约束。
-- 粉丝数 / 关注数 / 获赞数在 tb_user_info.fans、tb_user_info.followee、tb_user_info.like_count。
--
-- 执行顺序：先 user.sql，再本文件。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/follow.sql

CREATE SCHEMA IF NOT EXISTS community;

CREATE TABLE community.tb_follow (
    id              bigint    NOT NULL,                           -- 主键，雪花ID
    user_id         bigint    NOT NULL,                           -- 关注者用户ID，指向 tb_user.id
    follow_user_id  bigint    NOT NULL,                           -- 被关注用户ID，指向 tb_user.id
    create_time     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 关注时间。取消关注时物理删除本行
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_follow IS '用户关注表';
COMMENT ON COLUMN community.tb_follow.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_follow.user_id IS '关注者用户ID，指向 tb_user.id';
COMMENT ON COLUMN community.tb_follow.follow_user_id IS '被关注用户ID，指向 tb_user.id';
COMMENT ON COLUMN community.tb_follow.create_time IS '关注时间。取消关注时物理删除本行';
CREATE UNIQUE INDEX uk_follow_user_followee ON community.tb_follow (user_id, follow_user_id);
CREATE INDEX idx_follow_user_id_id ON community.tb_follow (user_id, id);
CREATE INDEX idx_follow_follow_user_id_id ON community.tb_follow (follow_user_id, id);
