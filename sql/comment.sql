-- 评论域建表脚本（PostgreSQL，schema：community）
--
-- 执行顺序：先 user.sql、bar.sql、post.sql，再本文件。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/comment.sql
--
-- tb_comment         评论元数据。列表只扫它。
-- tb_comment_content 评论正文。主键是 (post_id, year_month, content_id)。
-- tb_comment_like    评论点赞。取消点赞物理删除，才能释放唯一约束。

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

CREATE TABLE community.tb_comment (
    id               bigint       NOT NULL,                           -- 主键，雪花ID
    post_id          bigint       NOT NULL,                           -- 所属帖子ID
    user_id          bigint       NOT NULL,                           -- 发布者用户ID
    content_uuid     varchar(36)  NOT NULL DEFAULT '',                -- 评论内容UUID，对应 tb_comment_content.content_id；无正文为空串
    is_content_empty integer      NOT NULL DEFAULT 0,                 -- 内容是否为空：0不为空，1为空
    image_url        varchar(512) NOT NULL DEFAULT '',                -- 评论附加图片URL；无图为空串。完整访问地址，不使用 60 字符短链
    level            integer      NOT NULL DEFAULT 1,                 -- 级别：1一级评论，2二级评论
    reply_total      bigint       NOT NULL DEFAULT 0,                 -- 评论被回复次数，仅一级评论需要
    like_total       bigint       NOT NULL DEFAULT 0,                 -- 评论被点赞次数
    parent_id        bigint       NOT NULL DEFAULT 0,                 -- 父ID。对帖子的评论存帖子ID；二级评论存一级评论ID
    reply_comment_id bigint       NOT NULL DEFAULT 0,                 -- 回复哪条评论。0表示对帖子的评论；否则存被回复评论ID
    reply_user_id    bigint       NOT NULL DEFAULT 0,                 -- 回复的哪个用户，存用户ID；对帖子的评论为0
    is_top           integer      NOT NULL DEFAULT 0,                 -- 是否置顶：0不置顶，1置顶
    deleted          integer      NOT NULL DEFAULT 0,                 -- 逻辑删除：0未删除，1已删除
    create_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_comment IS '评论表';
COMMENT ON COLUMN community.tb_comment.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_comment.post_id IS '所属帖子ID';
COMMENT ON COLUMN community.tb_comment.user_id IS '发布者用户ID';
COMMENT ON COLUMN community.tb_comment.content_uuid IS '评论内容UUID，对应 tb_comment_content.content_id；无正文为空串';
COMMENT ON COLUMN community.tb_comment.is_content_empty IS '内容是否为空：0不为空，1为空';
COMMENT ON COLUMN community.tb_comment.image_url IS '评论附加图片URL；无图为空串';
COMMENT ON COLUMN community.tb_comment.level IS '级别：1一级评论，2二级评论';
COMMENT ON COLUMN community.tb_comment.reply_total IS '评论被回复次数，仅一级评论需要';
COMMENT ON COLUMN community.tb_comment.like_total IS '评论被点赞次数';
COMMENT ON COLUMN community.tb_comment.parent_id IS '父ID。对帖子的评论存帖子ID；二级评论存一级评论ID';
COMMENT ON COLUMN community.tb_comment.reply_comment_id IS '回复哪条评论。0表示对帖子的评论；否则存被回复评论ID';
COMMENT ON COLUMN community.tb_comment.reply_user_id IS '回复的哪个用户，存用户ID；对帖子的评论为0';
COMMENT ON COLUMN community.tb_comment.is_top IS '是否置顶：0不置顶，1置顶';
COMMENT ON COLUMN community.tb_comment.deleted IS '逻辑删除：0未删除，1已删除';
COMMENT ON COLUMN community.tb_comment.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_comment.update_time IS '更新时间，由触发器维护';
CREATE INDEX idx_comment_post_id ON community.tb_comment (post_id);
CREATE INDEX idx_comment_user_id ON community.tb_comment (user_id);
CREATE INDEX idx_comment_parent_id ON community.tb_comment (parent_id);
CREATE INDEX idx_comment_create_time ON community.tb_comment (create_time);
CREATE INDEX idx_comment_reply_comment_id ON community.tb_comment (reply_comment_id);
CREATE INDEX idx_comment_reply_user_id ON community.tb_comment (reply_user_id);
CREATE TRIGGER trg_tb_comment_touch
    BEFORE UPDATE ON community.tb_comment
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

-- 分区键是 (post_id, year_month)，content_id 是聚类列。PostgreSQL 用这三列做联合主键。
CREATE TABLE community.tb_comment_content (
    post_id    bigint      NOT NULL, -- 帖子ID，分区键
    year_month char(7)     NOT NULL, -- 发布年月，格式 yyyy-MM
    content_id varchar(36) NOT NULL, -- 评论内容ID，对应 tb_comment.content_uuid
    content    text        NOT NULL, -- 评论正文
    PRIMARY KEY (post_id, year_month, content_id)
);
COMMENT ON TABLE community.tb_comment_content IS '评论正文表';
COMMENT ON COLUMN community.tb_comment_content.post_id IS '帖子ID，分区键';
COMMENT ON COLUMN community.tb_comment_content.year_month IS '发布年月，格式 yyyy-MM';
COMMENT ON COLUMN community.tb_comment_content.content_id IS '评论内容ID，对应 tb_comment.content_uuid';
COMMENT ON COLUMN community.tb_comment_content.content IS '评论正文';

CREATE TABLE community.tb_comment_like (
    id          bigint    NOT NULL,                           -- 主键，雪花ID
    user_id     bigint    NOT NULL,                           -- 用户ID
    comment_id  bigint    NOT NULL,                           -- 评论ID
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间。取消点赞时物理删除本行
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_comment_like IS '评论点赞表';
COMMENT ON COLUMN community.tb_comment_like.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_comment_like.user_id IS '用户ID';
COMMENT ON COLUMN community.tb_comment_like.comment_id IS '评论ID';
COMMENT ON COLUMN community.tb_comment_like.create_time IS '创建时间。取消点赞时物理删除本行';
CREATE UNIQUE INDEX uk_comment_like_user_comment ON community.tb_comment_like (user_id, comment_id);

-- 评论消息发送失败后先落在这里，定时任务补发成功再删掉。
CREATE TABLE community.tb_comment_mq_outbox (
    id          bigint       NOT NULL,                           -- 主键，雪花ID
    topic       varchar(128) NOT NULL,                           -- 发送目的地，topic:tag
    payload     text         NOT NULL,                           -- 消息 JSON，里面的评论 ID 不再改变
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 写入时间
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_comment_mq_outbox IS '评论消息发送失败兜底表';
COMMENT ON COLUMN community.tb_comment_mq_outbox.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_comment_mq_outbox.topic IS '发送目的地，topic:tag';
COMMENT ON COLUMN community.tb_comment_mq_outbox.payload IS '消息 JSON。评论主键在第一次生成后保持不变';
COMMENT ON COLUMN community.tb_comment_mq_outbox.create_time IS '写入时间';
CREATE INDEX idx_comment_mq_outbox_create_time ON community.tb_comment_mq_outbox (create_time);
