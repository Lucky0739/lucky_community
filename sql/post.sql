-- 内容域建表脚本：帖子 / 帖子图片 / 帖子AI分析结果（PostgreSQL，schema：community）
--
-- 执行顺序：先 user.sql、bar.sql，再本文件。贴吧表在 bar.sql。
--
-- 用法：
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/user.sql
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/bar.sql
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/post.sql
--
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

-- 封面与张数冗余在主表：列表分页只扫 tb_post。
-- 完整正文在 Cassandra（keyspace community，按 post_id 读写）；本表只存摘要供列表展示。
-- 第一张图即封面，无图时 cover_image 为 NULL、image_count 为 0。
CREATE TABLE community.tb_post (
    id                   bigint        NOT NULL,                           -- 主键，雪花ID
    bar_id               bigint        NOT NULL,                           -- 所属贴吧ID
    user_id              bigint        NOT NULL,                           -- 作者用户ID
    title                varchar(64)   NOT NULL,                           -- 标题
    content_preview      varchar(256)  NOT NULL DEFAULT '',                -- 正文摘要，列表用；详情正文读 Cassandra
    visibility_scope     integer       NOT NULL DEFAULT 0,                 -- 可见范围：0公开，1仅自己，2仅粉丝
    cover_image          varchar(512),                                     -- 封面图URL，取第一张配图；无图为NULL
    image_count          integer       NOT NULL DEFAULT 0,                 -- 配图张数，与未删除的图片行数同步；无图为0
    deleted              integer       NOT NULL DEFAULT 0,                 -- 逻辑删除：0未删除，1已删除
    event_status         integer       NOT NULL DEFAULT 0,                 -- 帖子变更事件是否已投递给AI：0待投递，1已投递，2投递失败
    embed_status         integer       NOT NULL DEFAULT 0,                 -- 写入向量库的进度：0待处理，1处理中，2成功，3失败
    analysis_status      integer       NOT NULL DEFAULT 0,                 -- AI内容分析进度：0待处理，1处理中，2成功，3失败
    analysis_retry_count integer       NOT NULL DEFAULT 0,                 -- AI分析重试次数
    analysis_time        timestamp,                                        -- AI分析完成时间
    create_time          timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time          timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间，由触发器维护
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_post IS '帖子表';
COMMENT ON COLUMN community.tb_post.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_post.bar_id IS '所属贴吧ID';
COMMENT ON COLUMN community.tb_post.user_id IS '作者用户ID';
COMMENT ON COLUMN community.tb_post.title IS '标题';
COMMENT ON COLUMN community.tb_post.content_preview IS '正文摘要（列表展示）；完整正文在 Cassandra，主键与帖子 id 一致';
COMMENT ON COLUMN community.tb_post.visibility_scope IS '可见范围：0公开，1仅自己，2仅粉丝（关注作者的人）';
COMMENT ON COLUMN community.tb_post.cover_image IS '封面图URL，取第一张配图；无图为NULL';
COMMENT ON COLUMN community.tb_post.image_count IS '配图张数，与未删除的 tb_post_image 行数同步；无图为0';
COMMENT ON COLUMN community.tb_post.deleted IS '逻辑删除：0未删除，1已删除';
COMMENT ON COLUMN community.tb_post.event_status IS '帖子变更事件是否已投递给AI服务：0待投递，1已投递，2投递失败';
COMMENT ON COLUMN community.tb_post.embed_status IS '帖子写入向量库的进度：0待处理，1处理中，2成功，3失败。与 analysis_status 互不影响';
COMMENT ON COLUMN community.tb_post.analysis_status IS 'AI内容分析（风险结论）的进度：0待处理，1处理中，2成功，3失败。与 embed_status 互不影响';
COMMENT ON COLUMN community.tb_post.analysis_retry_count IS 'AI分析重试次数';
COMMENT ON COLUMN community.tb_post.analysis_time IS 'AI分析完成时间';
COMMENT ON COLUMN community.tb_post.create_time IS '创建时间';
COMMENT ON COLUMN community.tb_post.update_time IS '更新时间，由触发器维护';
-- 列表主查询 WHERE bar_id=? AND deleted=0 ORDER BY id DESC
CREATE INDEX idx_post_bar_id_id ON community.tb_post (bar_id, id);
CREATE INDEX idx_post_user_id_id ON community.tb_post (user_id, id);
CREATE INDEX idx_post_embed_status ON community.tb_post (embed_status);
CREATE INDEX idx_post_analysis_status ON community.tb_post (analysis_status);
CREATE TRIGGER trg_tb_post_touch
    BEFORE UPDATE ON community.tb_post
    FOR EACH ROW EXECUTE FUNCTION community.touch_update_time();

-- 删帖 / 改配图走逻辑删除，行留下给 object_name 孤儿对账。
CREATE TABLE community.tb_post_image (
    id          bigint       NOT NULL,                             -- 主键，雪花ID
    post_id     bigint       NOT NULL,                             -- 所属帖子ID
    url         varchar(512) NOT NULL,                             -- 图片访问URL
    object_name varchar(255) NOT NULL,                             -- MinIO对象名，用于孤儿对象对账；同一对象可被多行引用
    sort_no     integer      NOT NULL DEFAULT 0,                   -- 展示顺序，从0开始；第一张即封面
    deleted     integer      NOT NULL DEFAULT 0,                   -- 逻辑删除：0未删除，1已删除。删图留行，便于按对象名对账
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 创建时间
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_post_image IS '帖子图片表';
COMMENT ON COLUMN community.tb_post_image.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_post_image.post_id IS '所属帖子ID';
COMMENT ON COLUMN community.tb_post_image.url IS '图片访问URL';
COMMENT ON COLUMN community.tb_post_image.object_name IS 'MinIO对象名，用于孤儿对象对账；同一对象可被多行引用';
COMMENT ON COLUMN community.tb_post_image.sort_no IS '展示顺序，从0开始；第一张即封面';
COMMENT ON COLUMN community.tb_post_image.deleted IS '逻辑删除：0未删除，1已删除。删图留行，便于按 object_name 对账';
COMMENT ON COLUMN community.tb_post_image.create_time IS '创建时间';
CREATE INDEX idx_post_image_post_id_sort ON community.tb_post_image (post_id, sort_no);

-- uk_post_id 承担重跑幂等：ON CONFLICT (post_id) DO UPDATE，不留两条结论。
-- evidence_json 用 text：Java 侧按字符串读写，避免 jsonb 与 String 的类型处理器。
CREATE TABLE community.tb_post_analysis (
    id              bigint        NOT NULL,                           -- 主键，雪花ID
    post_id         bigint        NOT NULL,                           -- 帖子ID，唯一。同一帖重跑覆盖这一行
    summary         varchar(100)  DEFAULT '',                         -- AI生成摘要
    similar_post_ids text,                                            -- 相似帖子ID，JSON数组
    risk_level      varchar(16)   NOT NULL DEFAULT '无风险',          -- 风险等级：无风险、低、中、高
    risk_categories varchar(255)  DEFAULT '',                         -- 风险分类，逗号分隔：广告引流、辱骂攻击、色情低俗、侵权、引战、违法
    confidence      numeric(4, 3) NOT NULL DEFAULT 0,                 -- 置信度，0到1
    evidence_json   text,                                             -- 原文引用JSON数组。Java按字符串读写
    reasoning       varchar(512)  DEFAULT '',                         -- 判定理由
    has_conflict    integer       NOT NULL DEFAULT 0,                 -- 是否触发冲突：0否，1是
    conflict_type   varchar(32),                                      -- 冲突类型；无冲突为NULL
    retry_count     integer       NOT NULL DEFAULT 0,                 -- 回溯复核次数
    handler_action  varchar(32),                                      -- 吧务最终处置：空=还没人处理，未处置/警告/删帖/封禁=已经看过
    model_version   varchar(64)   DEFAULT '',                         -- 产出结论的模型或规则版本
    latency_ms      integer,                                          -- 端到端耗时，毫秒
    token_cost      integer,                                          -- token消耗
    create_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 结论写入时间
    PRIMARY KEY (id)
);
COMMENT ON TABLE community.tb_post_analysis IS '帖子AI分析结果表';
COMMENT ON COLUMN community.tb_post_analysis.id IS '主键，雪花ID';
COMMENT ON COLUMN community.tb_post_analysis.post_id IS '帖子ID，唯一。同一帖重跑覆盖这一行';
COMMENT ON COLUMN community.tb_post_analysis.summary IS 'AI生成的帖子摘要，最多100字';
COMMENT ON COLUMN community.tb_post_analysis.similar_post_ids IS '相似帖子ID，JSON数组';
COMMENT ON COLUMN community.tb_post_analysis.risk_level IS '风险等级：无风险、低、中、高';
COMMENT ON COLUMN community.tb_post_analysis.risk_categories IS '风险分类，逗号分隔。取值：广告引流、辱骂攻击、色情低俗、侵权、引战、违法';
COMMENT ON COLUMN community.tb_post_analysis.confidence IS '置信度，0到1';
COMMENT ON COLUMN community.tb_post_analysis.evidence_json IS '原文引用JSON数组。Java按字符串读写';
COMMENT ON COLUMN community.tb_post_analysis.reasoning IS '判定理由';
COMMENT ON COLUMN community.tb_post_analysis.has_conflict IS '是否触发冲突：0否，1是';
COMMENT ON COLUMN community.tb_post_analysis.conflict_type IS '冲突类型；无冲突为NULL';
COMMENT ON COLUMN community.tb_post_analysis.retry_count IS '回溯复核次数';
COMMENT ON COLUMN community.tb_post_analysis.handler_action IS '吧务最终处置：空=还没人处理，未处置/警告/删帖/封禁=已经看过';
COMMENT ON COLUMN community.tb_post_analysis.model_version IS '产出结论的模型或规则版本';
COMMENT ON COLUMN community.tb_post_analysis.latency_ms IS '端到端耗时，毫秒';
COMMENT ON COLUMN community.tb_post_analysis.token_cost IS 'token消耗';
COMMENT ON COLUMN community.tb_post_analysis.create_time IS '结论写入时间';
CREATE UNIQUE INDEX uk_post_id ON community.tb_post_analysis (post_id);
