-- 评论域建表脚本（PostgreSQL，schema：community）
--
-- 执行顺序：先 user.sql、bar.sql、post.sql，再本文件。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/comment.sql
--
-- tb_comment         评论元数据。列表只扫它。
-- tb_comment_content 评论正文。
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
    user_id          bigint       NOT NULL,                           -- 评论作者用户ID
    content_uuid     varchar(36)  NOT NULL DEFAULT '',                -- 正文UUID，对应 tb_comment_content；无正文为空串
    is_content_empty integer      NOT NULL DEFAULT 0,                 -- 正文是否为空：0有正文，1纯图片
    image_url        varchar(512) NOT NULL DEFAULT '',                -- 配图直链；无图为空串
    level            integer      NOT NULL DEFAULT 1,                 -- 楼层：1一级评论，2二级回复
    reply_total      bigint       NOT NULL DEFAULT 0,                 -- 被回复次数，仅一级评论维护
    like_total       bigint       NOT NULL DEFAULT 0,                 -- 被点赞次数
    parent_id        bigint       NOT NULL DEFAULT 0,                 -- 一级评论存帖子ID，二级回复存所属一级评论ID
    reply_comment_id bigint       NOT NULL DEFAULT 0,                 -- 直接回复的评论ID；一级评论为0
    reply_user_id    bigint       NOT NULL DEFAULT 0,                 -- 被回复的用户ID；一级评论为0
    is_top           integer      NOT NULL DEFAULT 0,                 -- 是否置顶：0否，1是
    deleted          integer      NOT NULL DEFAULT 0,                 -- 逻辑删除：0未删除，1已删除
    create_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_comment IS '评论元数据表';
COMMENT ON COLUMN community.tb_comment.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_comment.post_id IS '所属帖子ID';
COMMENT ON COLUMN community.tb_comment.user_id IS '评论作者用户ID';
COMMENT ON COLUMN community.tb_comment.content_uuid IS '正文UUID，对应 tb_comment_content.content_uuid；无正文为空串';
COMMENT ON COLUMN community.tb_comment.is_content_empty IS '正文是否为空：0有正文，1纯图片';
COMMENT ON COLUMN community.tb_comment.image_url IS '配图直链；无图为空串';
COMMENT ON COLUMN community.tb_comment.level IS '楼层：1一级评论，2二级回复';
COMMENT ON COLUMN community.tb_comment.reply_total IS '被回复次数，仅一级评论维护';
COMMENT ON COLUMN community.tb_comment.like_total IS '被点赞次数';
COMMENT ON COLUMN community.tb_comment.parent_id IS '一级评论存帖子ID，二级回复存所属一级评论ID';
COMMENT ON COLUMN community.tb_comment.reply_comment_id IS '直接回复的评论ID；一级评论为0';
COMMENT ON COLUMN community.tb_comment.reply_user_id IS '被回复的用户ID；一级评论为0';
COMMENT ON COLUMN community.tb_comment.is_top IS '是否置顶：0否，1是';
COMMENT ON COLUMN community.tb_comment.deleted IS '逻辑删除：0未删除，1已删除';
COMMENT ON COLUMN community.tb_comment.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_comment.update_time IS '更新时间，由触发器维护';
CREATE INDEX idx_comment_post_level_top_id ON community.tb_comment (post_id, level, is_top, id);
CREATE INDEX idx_comment_parent_level_id ON community.tb_comment (parent_id, level, id);
CREATE INDEX idx_comment_user_id_id ON community.tb_comment (user_id, id);
CREATE INDEX idx_comment_reply_comment_id ON community.tb_comment (reply_comment_id);
CREATE TRIGGER trg_tb_comment_touch
    BEFORE UPDATE ON community.tb_comment
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

CREATE TABLE community.tb_comment_content (
    content_uuid varchar(36) NOT NULL, -- 正文UUID，主键
    post_id      bigint      NOT NULL, -- 所属帖子ID
    year_month   char(7)     NOT NULL, -- 发布年月，格式 yyyy-MM
    content      text        NOT NULL, -- 评论正文
    PRIMARY KEY (content_uuid)
);
COMMENT ON TABLE community.tb_comment_content IS '评论正文表';
COMMENT ON COLUMN community.tb_comment_content.content_uuid IS '正文UUID，主键';
COMMENT ON COLUMN community.tb_comment_content.post_id IS '所属帖子ID';
COMMENT ON COLUMN community.tb_comment_content.year_month IS '发布年月，格式 yyyy-MM';
COMMENT ON COLUMN community.tb_comment_content.content IS '评论正文';
CREATE INDEX idx_comment_content_post_year_month ON community.tb_comment_content (post_id, year_month);

CREATE TABLE community.tb_comment_like (
    id          bigint    NOT NULL,                           -- 主键，雪花ID
    user_id     bigint    NOT NULL,                           -- 点赞用户ID
    comment_id  bigint    NOT NULL,                           -- 被点赞的评论ID
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 点赞时间。取消点赞时物理删除本行
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_comment_like IS '评论点赞表';
COMMENT ON COLUMN community.tb_comment_like.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_comment_like.user_id IS '点赞用户ID';
COMMENT ON COLUMN community.tb_comment_like.comment_id IS '被点赞的评论ID';
COMMENT ON COLUMN community.tb_comment_like.create_time IS '点赞时间。取消点赞时物理删除本行';
CREATE UNIQUE INDEX uk_comment_like_user_comment ON community.tb_comment_like (user_id, comment_id);
CREATE INDEX idx_comment_like_comment_id ON community.tb_comment_like (comment_id);
