-- 用户与权限域建表脚本（PostgreSQL，schema：community）
--
-- 执行顺序：本文件 → bar.sql → post.sql → comment.sql / thumb.sql / favorite.sql / post_count.sql / follow.sql。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/user.sql
--
-- 重复执行会因表已存在而报错（没有 IF NOT EXISTS）。
-- 状态和计数用 integer / bigint，update_time 靠 community.touch_update_time 触发器维护。
-- 同一 schema 内索引名不能重名。

CREATE SCHEMA IF NOT EXISTS community;
ALTER DATABASE community SET search_path TO community, public;

CREATE OR REPLACE FUNCTION community.touch_update_time()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

-- 用户扩展信息
CREATE TABLE community.tb_user_info (
    id          bigint      NOT NULL,                           -- 主键，雪花ID
    user_id     bigint      NOT NULL,                           -- 用户ID，唯一，指向 tb_user.id
    city        varchar(64) DEFAULT '',                         -- 城市
    introduce   varchar(128),                                   -- 个人简介
    fans        integer     DEFAULT 0,                          -- 粉丝数
    followee    integer     DEFAULT 0,                          -- 关注数
    gender      integer     DEFAULT 0,                          -- 性别：0男，1女
    birthday    date,                                           -- 生日
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_user_info IS '用户扩展信息表';
COMMENT ON COLUMN community.tb_user_info.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_user_info.user_id IS '用户ID，唯一，指向 tb_user.id';
COMMENT ON COLUMN community.tb_user_info.city IS '城市';
COMMENT ON COLUMN community.tb_user_info.introduce IS '个人简介';
COMMENT ON COLUMN community.tb_user_info.fans IS '粉丝数';
COMMENT ON COLUMN community.tb_user_info.followee IS '关注数';
COMMENT ON COLUMN community.tb_user_info.gender IS '性别：0男，1女';
COMMENT ON COLUMN community.tb_user_info.birthday IS '生日';
COMMENT ON COLUMN community.tb_user_info.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_user_info.update_time IS '更新时间，由触发器维护';
CREATE UNIQUE INDEX uk_user_info_user_id ON community.tb_user_info (user_id);
CREATE TRIGGER trg_tb_user_info_touch
    BEFORE UPDATE ON community.tb_user_info
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

-- phone 可空：逻辑删除时置 NULL 以释放唯一索引。
-- PostgreSQL 唯一索引允许多个 NULL，未删除行的手机号仍然唯一。
CREATE TABLE community.tb_user (
    id          bigint       NOT NULL,                           -- 主键，雪花ID
    phone       varchar(11),                                     -- 登录手机号。逻辑删除时置NULL，以释放唯一索引
    password    varchar(128) DEFAULT '',                         -- 密码哈希
    nick_name   varchar(32)  DEFAULT '',                         -- 昵称
    icon        varchar(255) DEFAULT '',                         -- 头像URL
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    deleted     integer      NOT NULL DEFAULT 0,                 -- 逻辑删除：0未删除，1已删除
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_user IS '用户表';
COMMENT ON COLUMN community.tb_user.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_user.phone IS '登录手机号。逻辑删除时置NULL，以释放唯一索引';
COMMENT ON COLUMN community.tb_user.password IS '密码哈希';
COMMENT ON COLUMN community.tb_user.nick_name IS '昵称';
COMMENT ON COLUMN community.tb_user.icon IS '头像URL';
COMMENT ON COLUMN community.tb_user.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_user.update_time IS '更新时间，由触发器维护';
COMMENT ON COLUMN community.tb_user.deleted IS '逻辑删除：0未删除，1已删除';
CREATE UNIQUE INDEX uniqe_key_phone ON community.tb_user (phone);
CREATE TRIGGER trg_tb_user_touch
    BEFORE UPDATE ON community.tb_user
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

CREATE TABLE community.tb_user_phone (
    id          bigint       NOT NULL,                           -- 主键，雪花ID
    user_id     bigint       NOT NULL,                           -- 用户ID
    phone       varchar(512) NOT NULL,                           -- 手机号审计副本。用户删除后 tb_user.phone 会置NULL，这里仍保留原号码
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_user_phone IS '用户手机表';
COMMENT ON COLUMN community.tb_user_phone.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_user_phone.user_id IS '用户ID';
COMMENT ON COLUMN community.tb_user_phone.phone IS '手机号审计副本。用户逻辑删除后 tb_user.phone 会置NULL，这里仍保留原号码';
COMMENT ON COLUMN community.tb_user_phone.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_user_phone.update_time IS '更新时间，由触发器维护';
CREATE INDEX idx_user_phone_phone ON community.tb_user_phone (phone);
CREATE TRIGGER trg_tb_user_phone_touch
    BEFORE UPDATE ON community.tb_user_phone
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

CREATE TABLE community.tb_role (
    id          bigint       NOT NULL,                           -- 主键
    role_name   varchar(32)  NOT NULL,                           -- 角色名称
    role_code   varchar(32)  NOT NULL,                           -- 角色编码，唯一。如 NORMAL_USER、BAR_ADMIN、PLATFORM_ADMIN
    description varchar(128) DEFAULT '',                         -- 角色说明
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_role IS '角色表';
COMMENT ON COLUMN community.tb_role.id IS '主键';
COMMENT ON COLUMN community.tb_role.role_name IS '角色名称';
COMMENT ON COLUMN community.tb_role.role_code IS '角色编码，唯一。如 NORMAL_USER、BAR_ADMIN、PLATFORM_ADMIN';
COMMENT ON COLUMN community.tb_role.description IS '角色说明';
COMMENT ON COLUMN community.tb_role.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_role.update_time IS '更新时间，由触发器维护';
CREATE UNIQUE INDEX uk_role_code ON community.tb_role (role_code);
CREATE TRIGGER trg_tb_role_touch
    BEFORE UPDATE ON community.tb_role
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

CREATE TABLE community.tb_user_role (
    id          bigint    NOT NULL,                           -- 主键，雪花ID
    user_id     bigint    NOT NULL,                           -- 用户ID，指向 tb_user.id
    role_id     bigint    NOT NULL,                           -- 角色ID，指向 tb_role.id
    deleted     integer   NOT NULL DEFAULT 0,                 -- 逻辑删除：0未删除，1已删除。唯一约束含本列，撤销后可再次授予
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id),
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id, deleted)
);
COMMENT ON TABLE community.tb_user_role IS '用户角色关联表';
COMMENT ON COLUMN community.tb_user_role.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_user_role.user_id IS '用户ID，指向 tb_user.id';
COMMENT ON COLUMN community.tb_user_role.role_id IS '角色ID，指向 tb_role.id';
COMMENT ON COLUMN community.tb_user_role.deleted IS '逻辑删除：0未删除，1已删除。唯一约束含本列，便于撤销后再次授予';
COMMENT ON COLUMN community.tb_user_role.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_user_role.update_time IS '更新时间，由触发器维护';
CREATE INDEX idx_user_role_role_id ON community.tb_user_role (role_id);
CREATE INDEX idx_user_role_user_id ON community.tb_user_role (user_id);
CREATE TRIGGER trg_tb_user_role_touch
    BEFORE UPDATE ON community.tb_user_role
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

-- 贴吧吧务。id 自增，实体 IdType.AUTO
CREATE TABLE community.tb_bar_manager (
    id            bigint    GENERATED BY DEFAULT AS IDENTITY,  -- 主键，自增
    bar_id        bigint    NOT NULL,                           -- 贴吧ID
    user_id       bigint    NOT NULL,                           -- 被任命的吧务用户ID
    grant_user_id bigint    NOT NULL,                           -- 执行任命的用户ID
    grant_time    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 任命时间
    status        integer   NOT NULL DEFAULT 1,                 -- 这条任命是否有效：1生效，0已撤销
    create_time   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_bar_manager IS '贴吧吧务任命表';
COMMENT ON COLUMN community.tb_bar_manager.id IS '主键，自增';
COMMENT ON COLUMN community.tb_bar_manager.bar_id IS '贴吧ID';
COMMENT ON COLUMN community.tb_bar_manager.user_id IS '被任命的吧务用户ID';
COMMENT ON COLUMN community.tb_bar_manager.grant_user_id IS '执行任命的用户ID';
COMMENT ON COLUMN community.tb_bar_manager.grant_time IS '任命时间';
COMMENT ON COLUMN community.tb_bar_manager.status IS '这条吧务任命是否有效：1生效，0已撤销';
COMMENT ON COLUMN community.tb_bar_manager.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_bar_manager.update_time IS '更新时间，由触发器维护';
CREATE UNIQUE INDEX uk_bar_user ON community.tb_bar_manager (bar_id, user_id);
CREATE INDEX idx_bar_manager_bar_id ON community.tb_bar_manager (bar_id);
CREATE INDEX idx_bar_manager_user_id ON community.tb_bar_manager (user_id);
CREATE TRIGGER trg_tb_bar_manager_touch
    BEFORE UPDATE ON community.tb_bar_manager
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

INSERT INTO community.tb_role (id, role_name, role_code, description) VALUES
    (1, '普通吧友', 'NORMAL_USER', '用户注册后的默认身份'),
    (2, '本吧吧务', 'BAR_ADMIN', '由大吧/平台管理员任命'),
    (3, '平台官方管理员', 'PLATFORM_ADMIN', '系统后台新增，允许多个');

INSERT INTO community.tb_user (id, phone, password, nick_name, icon)
VALUES (1, '13800138000', '$2a$10$xxxx', '平台管理员', '');

INSERT INTO community.tb_user_info (id, user_id, city, introduce, fans, followee, gender, birthday)
VALUES (1, 1, '广州', '平台后台管理员账号', 0, 0, 0, NULL);

INSERT INTO community.tb_user_role (id, user_id, role_id)
VALUES (1, 1, 3);

-- 贴吧见 bar.sql，帖子见 post.sql
