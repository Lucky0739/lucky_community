-- 贴吧建表脚本（PostgreSQL，schema：community）
--
-- 执行顺序：先 user.sql，再本文件，然后 post.sql。种子贴吧 owner_user_id=1 指向平台管理员。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/bar.sql
--
-- 重复执行会因表已存在而报错。

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

-- 贴吧。刻意没有 deleted 列：mybatis-plus 的 logic-delete-field 按字段名全局匹配，
-- 没有该列的表不能在实体上声明 deleted，否则查询会带 AND deleted=0。
CREATE TABLE community.tb_bar (
    id            bigint       NOT NULL,                             -- 主键，雪花ID
    name          varchar(64)  NOT NULL,                             -- 贴吧名称，全站唯一
    description   varchar(255) DEFAULT '',                           -- 贴吧简介
    avatar        varchar(255) DEFAULT '',                           -- 贴吧头像URL
    post_count    integer      NOT NULL DEFAULT 0,                   -- 帖子数：该吧未删除的帖子数
    owner_user_id bigint,                                            -- 吧主用户ID，指向 tb_user.id
    status        integer      NOT NULL DEFAULT 1,                   -- 贴吧是否可用：1正常（可发帖、可出现在列表），0停用
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 创建时间
    update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_bar IS '贴吧表';
COMMENT ON COLUMN community.tb_bar.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_bar.name IS '贴吧名称，全站唯一';
COMMENT ON COLUMN community.tb_bar.description IS '贴吧简介';
COMMENT ON COLUMN community.tb_bar.avatar IS '贴吧头像URL';
COMMENT ON COLUMN community.tb_bar.post_count IS '帖子数：该吧未删除的帖子数';
COMMENT ON COLUMN community.tb_bar.owner_user_id IS '吧主用户ID，指向 tb_user.id';
COMMENT ON COLUMN community.tb_bar.status IS '贴吧是否可用：1正常（可发帖、可出现在列表），0停用';
COMMENT ON COLUMN community.tb_bar.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_bar.update_time IS '更新时间，由触发器维护';
CREATE UNIQUE INDEX uk_bar_name ON community.tb_bar (name);
CREATE TRIGGER trg_tb_bar_touch
    BEFORE UPDATE ON community.tb_bar
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

INSERT INTO community.tb_bar (id, name, description, owner_user_id, status)
VALUES (1001, '测试吧', '联调用的测试贴吧', 1, 1);
