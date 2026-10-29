-- 关注域建表脚本（PostgreSQL，schema：community）
--
-- 用户关注贴吧。取消关注物理删除，才能释放唯一约束。
-- 用户关注的吧数量在 tb_user_info.followee；贴吧粉丝数在 tb_bar_count.fan_count（见 bar_count.sql）。
--
-- 已有旧表（follow_user_id）时执行 sql/follow_to_bar.sql。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/follow.sql

CREATE SCHEMA IF NOT EXISTS community;

CREATE TABLE IF NOT EXISTS community.tb_follow (
    id          bigint    NOT NULL,                           -- 主键，雪花ID
    user_id     bigint    NOT NULL,                           -- 关注者用户ID，指向 tb_user.id
    bar_id      bigint    NOT NULL,                           -- 被关注贴吧ID，指向 tb_bar.id
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 关注时间。取消关注时物理删除本行
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_follow IS '用户关注贴吧表';
COMMENT ON COLUMN community.tb_follow.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_follow.user_id IS '关注者用户ID，指向 tb_user.id';
COMMENT ON COLUMN community.tb_follow.bar_id IS '被关注贴吧ID，指向 tb_bar.id';
COMMENT ON COLUMN community.tb_follow.create_time IS '关注时间。取消关注时物理删除本行';
CREATE UNIQUE INDEX IF NOT EXISTS uk_follow_user_bar ON community.tb_follow (user_id, bar_id);
CREATE INDEX IF NOT EXISTS idx_follow_user_id_id ON community.tb_follow (user_id, id);
CREATE INDEX IF NOT EXISTS idx_follow_bar_id ON community.tb_follow (bar_id);
