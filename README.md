# Lucky Community

贴吧风格的社区平台：吧、帖、评论、点赞收藏关注、搜索、管理后台，以及旁路的多智能体内容治理。

业务元数据在 PostgreSQL，帖子/评论正文在 Cassandra，热点读走 Redis，检索走 Elasticsearch。Java 微服务经 Gateway 对外；Python LangGraph 服务只出审核建议，不直接处置帖子。

## 功能

- 手机号注册登录；短 access Token + HttpOnly refresh Cookie
- 贴吧、发帖、Feed、帖子详情；图片存 MinIO
- 一二级评论；点赞、收藏、关注贴吧
- 帖子 / 贴吧搜索（IK 分词、高亮）
- 吧主 / 吧务 / 平台管理员
- 发帖后旁路治理：摘要、同吧相似帖、风险建议；看图按用户文风起草标题正文（需用户确认后再发）

## 架构

```
Vue 3 (5173)
    │  /user /bar /post /comment /thumb /favorite /follow /search /admin
    ▼
Gateway 8001
    ├─ user        8002    认证、资料、角色
    ├─ interaction 8003    点赞、收藏、关注
    ├─ post        8004    吧 / 帖 / Feed / 管理 / 调 AI
    ├─ kv          8005    Cassandra 正文
    ├─ comment     8006    评论
    └─ search      8007    Elasticsearch
              │
              ▼
 PostgreSQL · Redis · Cassandra · ES · RocketMQ · MinIO · Nacos
              │
              └── HTTP 旁路 ──► AI Agent 127.0.0.1:8000（FastAPI + LangGraph）
```

存储按查询拆：

| 数据 | 放哪 | 原因 |
| --- | --- | --- |
| 用户、吧、帖元数据、评论元数据、关系、计数 | PostgreSQL | 事务、游标分页、关联查询 |
| 帖子正文 | Cassandra `post_content`，主键 `id` | 详情一次读一篇 |
| 评论正文 | Cassandra `comment_content`，主键 `((post_id, year_month), content_id)` | 一页批量点查，按月切分区防热帖撑爆 |
| 列表 ID、计数、会话、布隆过滤器 | Redis | 热点读、点赞先写缓存再异步落库 |
| 帖 / 吧检索 | Elasticsearch | 分词与高亮 |
| 帖子/吧向量、提示词 | PostgreSQL `community`（`ai_agent` 账号） | Java 不读写这两张 AI 表 |

## 仓库结构

```
lucky_community/
├── lucky-gateway                 统一入口
├── lucky-user-service            用户与认证
├── lucky-post-service            贴吧、帖子、缓存、AI 旁路
├── lucky-comment-service         评论
├── lucky-interaction-service     点赞、收藏、关注
├── lucky-kv-service              Cassandra KV（api + biz）
├── lucky-search-service          搜索
├── lucky-common                  公共模型、鉴权、MQ、文件
├── community-front               Vue 3 + Vite
├── luckyCommunity_AiAgent        Python 治理服务
├── sql/                          PostgreSQL 建表
├── sql/cassandra/init.cql        Cassandra 建表
├── config/nacos/common.yaml      各服务共享配置底稿
├── config/elasticsearch          带 IK 的 ES 镜像
└── docker-compose.yml            本地基础设施
```

部分 Java 模块还依赖内部包 `lucky-redis-framework`、`lucky-bloom-filter-framework`（同 `groupId`，需已安装到本地 Maven）。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring Cloud Alibaba 2025、OpenFeign、MyBatis |
| 前端 | Vue 3、Vite、Pinia、Vue Router |
| 注册 / 配置 | Nacos |
| 数据 | PostgreSQL 16（pgvector）、Cassandra 5、Redis Stack、Elasticsearch 8.15 + IK |
| 消息 / 文件 | RocketMQ 5、MinIO |
| CDC | Debezium Embedded（帖子 WAL → 缓存失效） |
| AI | Python 3.13、FastAPI、LangGraph、LangChain |

## 本地启动

需要：JDK 17、Maven 3.9+、Node 22+、Docker。AI 旁路另需 Python 3.13+（可用 [uv](https://github.com/astral-sh/uv)）。

本机跑 Java 时建议加上 `NACOS_DISCOVERY_IP=127.0.0.1`，避免注册成 Docker / VPN 的 `10.x` 地址，导致网关连超时。

### 1. 基础设施

```bash
docker compose up -d
```

| 组件 | 地址 |
| --- | --- |
| PostgreSQL | `127.0.0.1:5432`（postgres / postgres，库 `community`） |
| Redis | `127.0.0.1:6380` |
| Elasticsearch | http://127.0.0.1:9200 |
| Cassandra | `127.0.0.1:9042` |
| MinIO | API 9000，控制台 http://127.0.0.1:9001（minioadmin / minioadmin） |
| Nacos | API 8848，控制台 http://127.0.0.1:8080 |
| RocketMQ | NameServer 9876，Dashboard http://127.0.0.1:8082 |

Cassandra、Elasticsearch 首次起来较慢，等健康后再灌表。

帖子缓存 CDC 需要 PostgreSQL `wal_level=logical`。不满足时 post-service 会打日志并等待，不阻塞发帖。

### 2. 建表

容器首次启动会执行 `sql/init.sql`（schema、pgvector、`ai_agent` 账号）和 `sql/prompt.sql`（提示词表）。业务表需按顺序手工导入：

```bash
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/user.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/bar.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/post.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/post_cdc.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/comment.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/thumb.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/favorite.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/post_count.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/follow.sql
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/bar_count.sql
```

启用 AI 检索时再执行：

```bash
psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/ai_vector.sql
```

清空业务库后重建：先 `sql/rebuild.sql`，再按上面顺序导入（`rebuild.sql` 会 `DROP SCHEMA community`，提示词表也要重导 `sql/prompt.sql`）。

Cassandra 镜像不自动建表，首启后执行一次（幂等）：

```bash
docker exec -i community-cassandra cqlsh < sql/cassandra/init.cql
```

`sql/follow_to_bar.sql` 只给「关注用户」旧表迁到「关注贴吧」用，新库直接跑 `follow.sql` 即可。

### 3. Nacos 共享配置

各服务 `spring.config.import` 会拉 Group `LUCKY_COMMUNITY` 下的 `common.yaml` 和自身 `{application}.yaml`。Nacos 里没有配置时走本地 `application.yaml` 兜底。

建议在控制台发布一份 `config/nacos/common.yaml`：

- Data ID：`common.yaml`
- Group：`LUCKY_COMMUNITY`

生产环境的 JWT、数据源、MinIO 密钥放 Nacos，不要写进 Git。

### 4. Java 服务

在 IDE 里分别运行各模块的 `*Application`，或：

```bash
mvn -pl lucky-kv-service/lucky-kv-biz,lucky-user-service,lucky-interaction-service,lucky-post-service,lucky-comment-service,lucky-search-service,lucky-gateway -am spring-boot:run
```

更稳妥是按依赖逐个起：**kv → user → interaction → post → comment → search → gateway**。

| 服务 | 端口 | 入口类 |
| --- | --- | --- |
| lucky-gateway | 8001 | `com.lucky.gateway.GatewayApplication` |
| lucky-user-service | 8002 | `com.lucky.user.UserServiceApplication` |
| lucky-interaction-service | 8003 | `com.lucky.interaction.InteractionServiceApplication` |
| lucky-post-service | 8004 | `com.lucky.community.PostServiceApplication` |
| lucky-kv-biz | 8005 | `com.lucky.kv.KvServiceApplication` |
| lucky-comment-service | 8006 | `com.lucky.comment.CommentServiceApplication` |
| lucky-search-service | 8007 | `com.lucky.search.SearchServiceApplication` |

前端只访问网关 `8001`，不要直连业务端口。

### 5. 前端

```bash
cd community-front
npm install
npm run dev
```

浏览器打开 http://127.0.0.1:5173 。Vite 把 `/user`、`/bar`、`/post`、`/comment`、`/thumb`、`/favorite`、`/follow`、`/search`、`/admin` 代理到网关。

### 6. AI 旁路（可选）

不启动时，发帖主链路仍可用；post-service 调治理失败会吞掉，不影响落库。Java 默认请求 `http://127.0.0.1:8000`。

```bash
cd luckyCommunity_AiAgent
cp ai.toml.example ai.toml   # 填入模型 API Key
# 可选：cp .env.example .env
uv sync                      # 或 pip install -e .
python -m app.main
```

默认只监听 `127.0.0.1:8000`，接口无鉴权，不要绑 `0.0.0.0`。

| 接口 | 作用 |
| --- | --- |
| `GET /healthz` | 探活 |
| `POST /govern` | 完整治理（协调者调度抽取 / 检索 / 审核 / 复核） |
| `POST /moderate` | 快筛审核 |
| `POST /compose` | 看图起草，不落库 |
| `DELETE /vectors/{post_id}` | 删帖时清向量 |
| `PUT /vectors/{post_id}/action` | 吧务处置回写判例 |

更细的节点说明见 [`luckyCommunity_AiAgent/模块说明.md`](luckyCommunity_AiAgent/模块说明.md)，需求边界见 [`docs/需求-多智能体内容治理平台.md`](docs/需求-多智能体内容治理平台.md)。

## 核心设计（便于对照代码）

**评论一页正文。** 列表先从 Redis / PostgreSQL 拿元数据（`content_uuid`、`create_time`），`year_month` 由创建时间格式化成 `yyyy-MM`，再一次 RPC 打 KV：

```cql
SELECT * FROM comment_content
WHERE post_id = ?
  AND year_month IN (?)
  AND content_id IN (?);
```

分区键是 `(post_id, year_month)`，聚簇键是 `content_id`。普通一页约 10 条一级评论加各自首条回复，几乎落在同一个月分区。

**帖子正文。** 与 `tb_post` 同事务写入 Outbox，提交后再投影到 Cassandra，避免「PG 成功、KV 失败」各写一半。

**点赞 / 收藏。** 先改 Redis，RocketMQ 攒批落 PostgreSQL；计数按变更次数或时间窗口刷 `tb_post_count`。

**评论发布。** MQ 批量落库；列表用 ZSet 缓存 ID、Hash 缓存详情与计数。

**Feed / 详情缓存。** 首屏 VO 放本机 Caffeine（秒级 TTL，互动字段不进缓存）。详情逻辑过期 + 布隆过滤器挡穿透；Debezium 听 `tb_post` / `tb_post_image` 的 WAL 做失效。

**搜索。** 发帖后 MQ 同步 ES，写入按 `postId` 幂等，消费失败会重投。

**认证。** 标注 `@AuthRequired` 的接口才校验。短 Token 放响应体，长 Token 放 Cookie，`/user/refresh` 静默续期。

## 环境变量（常用）

本地都有默认值。覆盖时优先用进程环境变量或 Nacos。

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | 注册中心 |
| `NACOS_GROUP` | `LUCKY_COMMUNITY` | 配置 / 发现分组 |
| `NACOS_DISCOVERY_IP` | 空 | 本机 IDE 建议 `127.0.0.1` |
| `POSTGRES_URL` | `jdbc:postgresql://127.0.0.1:5432/community?currentSchema=community,public` | 业务库 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6380` | Redis |
| `CASSANDRA_PORT` | `9042` | KV 服务 |
| `ROCKETMQ_NAME_SERVER` | `127.0.0.1:9876` | 消息 |
| `ELASTICSEARCH_URIS` | `http://127.0.0.1:9200` | 搜索 |
| `JWT_SECRET` | 仅开发兜底 | 生产必须替换 |
| `AI_MODERATION_ENABLED` | `true` | 关闭则不调 Python |
| `AI_MODERATION_BASE_URL` | `http://127.0.0.1:8000` | 治理服务 |
| `AI_PORT` | `8000` | Python 监听端口 |
