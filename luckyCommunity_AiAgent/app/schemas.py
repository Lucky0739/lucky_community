"""跨语言契约：Java 侧通过 HTTP 传进来的请求体，与返回的审核结论。

字段形状对齐设计文档 4.4 的 RiskAssessment。
`extra="forbid"` 有两个作用：Pydantic 生成 JSON Schema 时会带
additionalProperties: false（结构化输出 API 的硬要求），同时防止模型多吐字段。

# 模型输出为什么有 Raw / Structured 两套

同一份字段结构有两个模型，职责不同，**别把它们合并**：

- `Raw*`：字段全部可选、类型全宽松。只用在「拿到什么算什么」的兜底解析上
  （`extract_json` + `model_validate`）。字段一旦设成必填，模型少吐一个键就会
  抛 ValidationError，把一次本可抢救的审核整个废掉。
- `Structured*`：字段**全部必填**并带 description，只喂给
  `with_structured_output` 生成 tool schema。

`Structured*` 之所以必须必填：tool schema 里没有 required 的字段，模型会理直气壮
地返回 `{}`，然后每次都走 `normalize()` 的兜底降级，等于白调一次昂贵模型。
`Structured*` 继承自 `Raw*`，所以 `normalize()` 原样接得住，不需要额外转换。
"""

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class RiskLevel(str, Enum):
    """风险等级。取值是中文，与文档 4.4 一致——这些字符串会直接落库。"""

    NONE = "无风险"
    LOW = "低"
    MEDIUM = "中"
    HIGH = "高"


class RiskCategory(str, Enum):
    """风险分类。六个取值由文档 4.4 固定，不允许模型自由发挥。"""

    AD = "广告引流"
    ABUSE = "辱骂攻击"
    PORNOGRAPHY = "色情低俗"
    INFRINGEMENT = "侵权"
    PROVOCATION = "引战"
    ILLEGAL = "违法"


class ContentType(str, Enum):
    HELP = "求助"
    SHARE = "分享"
    DISCUSSION = "讨论"
    QUESTION = "提问"
    OTHER = "其他"


class Evidence(BaseModel):
    """一条原文引用（已归一化的规范形态）。"""

    model_config = ConfigDict(extra="forbid")

    field: str = Field(description="命中的位置：title 或 content")
    quote: str = Field(description="原文中出现过的逐字连续子串，不得改写、不得拼接")
    offset: int = Field(
        default=-1,
        description="quote 在原文中的起始下标；由服务端计算并覆盖，模型无需输出",
    )


class RawEvidence(BaseModel):
    """LLM 原始输出里的引用。字段全是宽松类型，解析不做任何强约束。"""

    model_config = ConfigDict(extra="ignore")

    field: str = "content"
    quote: str = ""
    offset: int = -1


class StructuredEvidence(RawEvidence):
    """tool schema 里的引用条目。只把 `quote` 设成必填。

    `field` / `offset` 保持可选：空 `quote` 会被 `normalize()` 直接过滤掉，
    而 `offset` 本来就由 `validate_evidence()` 覆盖，模型填什么都不看。
    逼模型给这两个字段没有收益，只会增加它编造「不存在的下标」的机会。
    """

    quote: str = Field(
        description="原文中出现过的逐字连续子串，不得改写、不得拼接两处不相邻的文本"
    )


class RawAssessment(BaseModel):
    """LLM 的原始输出，**故意全部用宽松类型**。

    为什么不用 Enum：实测 `output_format` 的结构化输出约束在 Opus 4.8 上
    并没有真正生效（模型会返回带 ```json 围栏的 markdown，并且出现
    `风险等级 = '高风险'`、甚至把「广告引流」这个**分类**填进等级字段）。
    用严格 Enum 会让这些偏差直接抛 ValidationError，把一次本可抢救的审核整个废掉。

    职责分工：本模型只负责「把模型吐的东西尽量接下来」，
    归一化成规范取值是 `validate.normalize()` 的事。
    """

    model_config = ConfigDict(extra="ignore")

    risk_level: str = ""
    categories: list[str] = Field(default_factory=list)
    confidence: float = 0.0
    evidence: list[RawEvidence] = Field(default_factory=list)
    reasoning: str = ""


# 允许取值从 Enum 生成，不手抄。手抄的那份迟早会和 RiskLevel / RiskCategory 对不上，
# 而这两个 Enum 的取值是直接落库的，对不上就是脏数据。
_LEVEL_VALUES = "」「".join(level.value for level in RiskLevel)
_CATEGORY_VALUES = "」「".join(category.value for category in RiskCategory)


class StructuredAssessment(RawAssessment):
    """喂给 `with_structured_output` 的输出模型，字段必填，理由见模块 docstring。

    类型仍然是 `str` 而不是 Enum——**这一点和走不走 tool calling 无关**。
    模型真吐了「高风险」时，`normalize()` 能把它救回来、并在 warnings 里记一笔；
    换成 Enum 则直接 ValidationError，把结论整个丢掉。救得回来的偏差不该变成失败。

    `title` 会变成 tool 的名字（见 `convert_to_anthropic_tool`），
    默认取类名会得到 `StructuredAssessment` 这种给模型看的怪名字。
    """

    model_config = ConfigDict(extra="ignore", title="submit_assessment")

    risk_level: str = Field(
        description=f"风险等级，只能是「{_LEVEL_VALUES}」之一。"
        "不要写「高风险」「低风险」「安全」这类变体。"
        "拿不出原文引用时必须填「无风险」。"
    )
    categories: list[str] = Field(
        description=f"命中的风险分类，只能从「{_CATEGORY_VALUES}」里选，"
        "不要发明新类别。没有命中就给空数组。"
        "**不要把分类填进 risk_level 字段。**"
    )
    confidence: float = Field(
        description="对结论的把握程度，0~1 的小数。"
        "有明确原文引用支撑才给 0.8 以上，倾向性判断给 0.5 左右。"
    )
    evidence: list[StructuredEvidence] = Field(
        description="支撑结论的原文引用。引用必须能在标题或正文里原样找到，"
        "服务端会做子串匹配，对不上的一律丢弃。"
        "给不出引用时给空数组，并把 risk_level 填「无风险」。"
    )
    reasoning: str = Field(description="判定理由，一句话说清依据")


class RawScreen(BaseModel):
    """Haiku 粗筛的原始输出。字段全部可选，理由同 `RawAssessment`。"""

    model_config = ConfigDict(extra="ignore")

    verdict: str = ""
    confidence: float = 0.0
    reason: str = ""


class StructuredScreen(RawScreen):
    """喂给 `with_structured_output` 的粗筛输出模型。"""

    model_config = ConfigDict(extra="ignore", title="submit_screen_verdict")

    verdict: str = Field(description="只能是 clean 或 suspicious 之一")
    confidence: float = Field(description="把握程度，0~1 的小数")
    reason: str = Field(description="一句话说明判断依据")


class RiskAssessment(BaseModel):
    """文档 4.4 的 RiskAssessment（入库与返回给 Java 侧的规范形态）。"""

    model_config = ConfigDict(extra="forbid")

    risk_level: RiskLevel = Field(description="风险等级")
    categories: list[RiskCategory] = Field(
        default_factory=list, description="命中的风险分类；无风险时为空数组"
    )
    confidence: float = Field(ge=0, le=1, description="置信度 0~1")
    evidence: list[Evidence] = Field(
        default_factory=list,
        description="原文引用。为空时 risk_level 必须为「无风险」",
    )
    reasoning: str = Field(description="判定理由，一句话说清依据")


class ModerateRequest(BaseModel):
    """Java 侧传入的待审帖子。"""

    model_config = ConfigDict(extra="forbid")

    post_id: int = Field(description="帖子ID，原样回传，Java 侧据此落库")
    bar_id: int = Field(default=0, description="所属吧ID；完整治理流程必须提供")
    user_id: int | None = Field(default=None, description="发帖用户ID，仅作事件上下文")
    title: str = Field(description="帖子标题")
    content: str = Field(description="帖子正文")
    image_urls: list[str] = Field(default_factory=list, description="帖子图片URL")


class ModerateResponse(BaseModel):
    """审核结果。
    `degraded=True` 有两种含义，Java 侧要分开看：
    - `assessment is None`：**没拿到结论**（模型拒答 / 调用失败 / 解析失败）。
      必须按文档 6.2 标记「需人工」，绝不能当成「无风险」放过。
    - `assessment is not None`：拿到了结论，但引用未通过原文校验，
      风险等级已被降级为「无风险」且 `confidence=0`。结论可用，但值得人工复核。
    """

    post_id: int
    assessment: RiskAssessment | None = None
    degraded: bool = False
    degrade_reason: str | None = None
    model_version: str = ""
    latency_ms: int = 0
    token_cost: int = 0


class PostExtraction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str
    summary: str = Field(max_length=100)
    entities: list[str] = Field(default_factory=list)
    topics: list[str] = Field(default_factory=list)
    content_type: ContentType = ContentType.OTHER
    language: str = "zh"


class SimilarPost(BaseModel):
    model_config = ConfigDict(extra="forbid")

    post_id: int
    title: str
    snippet: str
    score: float = Field(ge=0, le=1)
    historical_action: str = "未处置"


class NormReference(BaseModel):
    model_config = ConfigDict(extra="forbid")

    doc_id: str
    section: str
    snippet: str
    score: float = Field(ge=0, le=1)


class RetrievalResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    similar_posts: list[SimilarPost] = Field(default_factory=list)
    norms: list[NormReference] = Field(default_factory=list)


class Conflict(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: str
    claim: str
    anchors: list[Evidence] = Field(default_factory=list)
    counter: str = ""
    question: str


class GovernanceResponse(BaseModel):
    """完整治理流程的返回体；保留 assessment 形状以兼容 Java 旧落库逻辑。"""

    model_config = ConfigDict(extra="forbid")

    post_id: int
    summary: str = ""
    extraction: PostExtraction | None = None
    retrieval: RetrievalResult = Field(default_factory=RetrievalResult)
    assessment: RiskAssessment | None = None
    conflicts: list[Conflict] = Field(default_factory=list)
    has_conflict: bool = False
    conflict_type: str | None = None
    retry_count: int = 0
    degraded: bool = False
    degraded_reasons: list[str] = Field(default_factory=list)
    degrade_reason: str | None = None
    model_version: str = ""
    latency_ms: int = 0
    token_cost: int = 0
