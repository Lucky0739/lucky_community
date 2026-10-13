"""FastAPI 入口。

暴露五个路由，按用途分三组：
  探活：  GET    /healthz               —— 存活 + 凭据/依赖自检
  审核：  POST   /moderate              —— 快筛审核一条帖子（漏斗）
          POST   /govern                —— 完整治理流程（抽取/检索/审核/冲突复核）
  发帖：  POST   /compose               —— 按用户文风看图写标题和正文
  向量维护：DELETE /vectors/{post_id}    —— 帖子删除时清掉它的向量
          PUT    /vectors/{post_id}/action —— 回写该帖的历史处置动作

没有鉴权，所以默认只监听 127.0.0.1（见 config.py）。这是**刻意的设计**：
Java 侧是同机调用，不需要跨机；而一旦监听 0.0.0.0，任何能碰到这个端口的人
都能拿你的 Anthropic key 刷账单。要跨机部署必须自行加上鉴权。
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

if __package__ in (None, ""):
    # 以脚本方式启动（python app/main.py）时 __package__ 是空的，
    # 下面的相对导入会直接报 "attempted relative import with no known parent package"。
    # 把项目根目录补进 sys.path 并声明包名，让 `python -m app.main` 和
    # `python app/main.py` 两种方式都能跑。推荐用前者。
    import sys
    from pathlib import Path

    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    __package__ = "app"

from .agents import moderation as moderation_agent
from .agents.composer import compose
from .config import settings
from .governance import govern
from .moderation import fingerprint, llm
from .retrieval import vector_store
from .schemas import ComposeRequest, ComposeResponse, GovernanceResponse, ModerateRequest, ModerateResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    # 缺凭据时**仍然启动**，只是每次审核都返回降级结果。
    # 这样 Java 侧的 fail-open 链路能被真实验证，而不是连不上直接超时。
    if llm.credentials_available():
        logger.info(
            "审核漏斗就绪：敏感词 → Redis指纹 → %s粗筛 → %s精判",
            settings.screen_model or "（关闭）",
            settings.model,
        )
    else:
        logger.error(
            "未找到模型凭据，漏斗第③层不可用，将只走敏感词与指纹。"
            "请填 ai.toml 的 api_key，或设置 ANTHROPIC_API_KEY / ANTHROPIC_AUTH_TOKEN。"
        )
    if fingerprint.available():
        logger.info(
            "审核指纹缓存已连接 Redis %s:%s",
            settings.redis_host,
            settings.redis_port,
        )
    else:
        logger.warning("审核指纹缓存未连接，第②层将跳过")
    if settings.langsmith_enabled:
        logger.info(
            "LangSmith 已开启，项目 %s。协调者的交接和抽取、审核、检索会挂在同一次 content_governance 下",
            settings.langsmith_project,
        )
    else:
        logger.info("LangSmith 未开启。填写 LANGSMITH_API_KEY 后，抽取、审核、检索才会上报")
    try:
        vector_store.ensure_schema()
        logger.info("pgvector 检索表已就绪")
    except Exception as exc:  # noqa: BLE001
        logger.warning("pgvector 暂不可用，检索与向量写入将自动降级：%s", exc)
    yield


app = FastAPI(title="LuckyCommunity AI Agent", version="0.1.0", lifespan=lifespan)

# 健康接口：进程是否活着，以及凭据、指纹缓存、粗筛模型和精判模型是否就绪。
@app.get("/healthz")
def healthz() -> dict:
    """存活探针，兼作依赖自检，供 Java 侧与运维巡检使用。

    无请求参数。永远返回 200，**探活失败也返回 200**——真实的降级状态
    靠响应体里的布尔字段体现，而不是靠 HTTP 状态码（否则 Java 侧的健康
    检查器会因为「依赖没配好」直接把进程判死，而进程本身是好的）。

    返回：
        status            固定 "ok"，仅表示 HTTP 层通
        credentials       是否找到模型凭据；False 时 /moderate、/govern 只会降级返回
        fingerprint_cache Redis 指纹层是否可用；False 时漏斗第②层被跳过
        screen_model      粗筛模型名；空字符串表示粗筛这一层被关闭
        judge_model       精判模型名
    """
    return {
        "status": "ok",
        "credentials": llm.credentials_available(),
        "fingerprint_cache": fingerprint.available(),
        "screen_model": settings.screen_model,
        "judge_model": settings.model,
    }

# 只跑审核漏斗，返回风险结论
@app.post("/moderate", response_model=ModerateResponse)
def moderate(req: ModerateRequest) -> ModerateResponse:
    """快筛审核一条帖子：敏感词 → Redis 指纹 → 模型粗筛 → 模型精判。

    这是审核链路里最短、最省 token 的一条，逐条帖子调用。请求体是
    `ModerateRequest`（标题 + 正文必填，bar_id / user_id 仅作上下文）。
    只读 req.title / req.content；其余字段目前不参与判定。

    返回 `ModerateResponse`。**重点看 `degraded` 而不是只看 assessment**：
    - degraded=True 且 assessment=None：没拿到结论（拒答/超时/解析失败），
      调用方必须按「需人工」处置，绝不能当成「无风险」放行；
    - degraded=True 且 assessment 非空：有结论但引用没通过原文校验，
      等级已被降级为「无风险」且 confidence=0，值得人工复核。
    详见 schemas.ModerateResponse 的说明。

    刻意用同步 def 而不是 async def：ChatAnthropic 是阻塞调用，写成 async
    会把事件循环卡死；同步 def 由 Starlette 丢进线程池，反而能并发。
    """
    outcome = moderation_agent.assess(req.title, req.content)
    return ModerateResponse(
        post_id=req.post_id,
        assessment=outcome.assessment,
        degraded=outcome.degraded,
        degrade_reason=outcome.degrade_reason,
        model_version=outcome.model_version,
        latency_ms=outcome.latency_ms,
        token_cost=outcome.token_cost,
    )

# 完整链路接口：抽取检索审核（完整治理）
@app.post("/govern", response_model=GovernanceResponse)
def govern_post(req: ModerateRequest) -> GovernanceResponse:
    """完整治理流程，比 /moderate 重得多。

    依次做：内容抽取、相似帖/规范检索、风险评估（三者尽量并行），
    随后冲突检测、必要时复核重判，最后汇总成报告。请求体同样是
    `ModerateRequest`，但这里 **bar_id 必须提供**（按吧做检索与规范匹配，
    缺省 0 会让检索范围失真）。

    返回 `GovernanceResponse`：在 assessment 之外还带 summary、extraction、
    retrieval（相似帖与规范引用）、conflicts / has_conflict / conflict_type、
    retry_count，以及 degraded / degraded_reasons。保留 assessment 形状是为了
    兼容 Java 侧既有的落库逻辑。同步 def 的原因同 /moderate。
    """
    return govern(req)


@app.post("/compose", response_model=ComposeResponse)
def compose_post(req: ComposeRequest) -> ComposeResponse:
    """按该用户以往帖子的写法，识别本次上传的图片，起草标题和正文。

    不落库。发帖接口仍要用户确认后再提交。图片由调用方先校验为本站帖子图。
    """
    outcome = compose(req.user_id, req.image_urls)
    return ComposeResponse(
        title=outcome.title,
        content=outcome.content,
        style_samples=outcome.style_samples,
        image_count=outcome.image_count,
        degraded=outcome.degraded,
        degrade_reason=outcome.degrade_reason,
        model_version=outcome.model_version,
        latency_ms=outcome.latency_ms,
        token_cost=outcome.token_cost,
    )


# 删帖时删掉该帖向量
@app.delete("/vectors/{post_id}", status_code=204)
def delete_vector(post_id: int) -> None:
    """删除某帖在 pgvector 里的全部向量分片。

    帖子被删除时由 Java 侧调用，避免已删内容继续出现在相似帖检索结果里。
    路径参数 post_id：帖子 ID。

    幂等：帖子本就没有向量（或已被删过）时不报错，一律返回 204 空响应。
    """
    vector_store.delete_post(post_id)

# 吧务处置后，把处置动作写回向量表，供以后检索当判例
@app.put("/vectors/{post_id}/action", status_code=204)
def update_vector_action(post_id: int, action: str) -> None:
    """回写某帖的历史处置动作，供后续相似帖检索带出 `historical_action`。

    比如帖子被判违规删掉，Java 侧调一次，之后检索到相似内容时审核模型
    能看到「这条历史相似帖当时被这么处理了」作为参考。

    参数：
        post_id  路径参数，帖子 ID
        action   查询参数（?action=已删帖），处置动作原文，原样存进
                 post_vectors.historical_action

    幂等：帖子无向量时 UPDATE 命中 0 行，不报错，返回 204 空响应。
    """
    vector_store.update_action(post_id, action)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=settings.host, port=settings.port)
