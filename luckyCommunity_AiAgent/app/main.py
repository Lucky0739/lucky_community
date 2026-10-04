"""FastAPI 入口。

只暴露两个路由：
  GET  /healthz   —— 存活 + 凭据自检
  POST /moderate  —— 审核一条帖子

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
from .config import settings
from .governance import govern
from .moderation import fingerprint, llm
from .retrieval import vector_store
from .schemas import GovernanceResponse, ModerateRequest, ModerateResponse

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
    try:
        vector_store.ensure_schema()
        logger.info("pgvector 检索表已就绪")
    except Exception as exc:  # noqa: BLE001
        logger.warning("pgvector 暂不可用，检索与向量写入将自动降级：%s", exc)
    yield


app = FastAPI(title="LuckyCommunity AI Agent", version="0.1.0", lifespan=lifespan)


@app.get("/healthz")
def healthz() -> dict:
    return {
        "status": "ok",
        "credentials": llm.credentials_available(),
        "fingerprint_cache": fingerprint.available(),
        "screen_model": settings.screen_model,
        "judge_model": settings.model,
    }


# 刻意用同步 def 而不是 async def：ChatAnthropic 是阻塞调用，
@app.post("/moderate", response_model=ModerateResponse)
def moderate(req: ModerateRequest) -> ModerateResponse:
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


@app.post("/govern", response_model=GovernanceResponse)
def govern_post(req: ModerateRequest) -> GovernanceResponse:
    """完整流程：抽取、检索、审核并行，随后冲突检测、复核和报告。"""
    return govern(req)


@app.delete("/vectors/{post_id}", status_code=204)
def delete_vector(post_id: int) -> None:
    vector_store.delete_post(post_id)


@app.put("/vectors/{post_id}/action", status_code=204)
def update_vector_action(post_id: int, action: str) -> None:
    vector_store.update_action(post_id, action)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=settings.host, port=settings.port)
