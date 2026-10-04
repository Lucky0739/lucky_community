"""审核提示词。

硬约束（文档 4.4）写在这里只是「告诉模型该怎么做」；真正兜底的是
`validate.py` 里的归一化与引用子串校验——提示词管不住的，代码得管住。

<h3>为什么正文里还要写一遍 JSON 结构</h3>
结论主要靠**强制工具调用**拿（见 `llm.py`）。工具定义里已经有完整的字段结构了，
照理说正文里再贴一遍是重复。之所以留着，是为了那条手写解析的兜底：
工具调用一旦失败（模型没按 schema 调用、被拒答、被截断），`extract_json`
要靠正文里的 JSON 才有的可解析。没有这段结构说明，兜底就形同虚设。

所以提示词里也明说了「调用失败就把 JSON 写正文」——这条兜底协议要让模型知道，
不能只写在代码注释里。
"""

import json
from dataclasses import replace

from ..schemas import StructuredAssessment
from .prompt_store import PromptValue, get_prompt

# 从 Pydantic 模型直接生成 schema 文本，而不是手抄一份——
# 手抄的迟早会和代码里的模型对不上。
# 取 Structured* 而不是 Raw*：它带 description 和 required，
# 正好就是 tool schema 的内容，提示词与工具说的是同一件事。
_SCHEMA_TEXT = json.dumps(
    StructuredAssessment.model_json_schema(), ensure_ascii=False, indent=2
)

SYSTEM_PROMPT_KEY = "moderation.judge.system"
SCREEN_SYSTEM_PROMPT_KEY = "moderation.screen.system"
USER_PROMPT_KEY = "moderation.user"

_SYSTEM_PROMPT_TEMPLATE = """你是社区平台内容风控系统的审核模块，负责按平台规则给用户发布的帖子做风险标注。
这是平台对公开内容履行治理义务的常规流程，输出会作为吧务人工复核的参考。

# 判定范围

只判定以下六类风险，不要发明新的类别：

- 广告引流：推广站外商品、联系方式、拉群、刷单等
- 辱骂攻击：针对特定人的侮辱、威胁、人肉
- 色情低俗：色情描写、软色情暗示、低俗擦边
- 侵权：盗用他人作品、泄露他人隐私
- 引战：刻意挑动群体对立、地域攻击
- 违法：涉赌毒枪、诈骗、政治敏感等明确违法内容

# 输出格式

**调用 `submit_assessment` 工具提交结论**，这是主要方式。
不要在正文里解释你的判断过程。

万一没能成功调用工具（不确定参数怎么填、调用被拒绝等），就退而求其次：
**在正文里直接输出一个同样结构的 JSON 对象**，不要加解释文字，
也不要用 markdown 代码块包裹。服务端两种都能接住。

结构如下：

{{schema_text}}

各字段的取值必须严格从下面的允许集合里选：

- `risk_level`：只能是「无风险」「低」「中」「高」四个值之一。
  不要写「高风险」「低风险」「安全」这类变体，就写一个字或「无风险」。
- `categories`：只能是「广告引流」「辱骂攻击」「色情低俗」「侵权」「引战」「违法」
  六个值之一；没有命中就给空数组。**不要把分类填进 risk_level 字段。**

# 硬性约束（违反会导致结论被服务端丢弃）

**约束一：无引用不下结论。**
`evidence` 数组为空时，`risk_level` 必须是「无风险」。不要凭感觉给风险等级——
拿不出原文片段，就等于没有依据。

**约束二：引用必须是原文的逐字连续子串。**
`quote` 字段的值必须能在帖子标题或正文里被原样找到，一个字都不能改。
不要改写、不要拼接两处不相邻的文本、不要省略中间的字符。
服务端会做子串匹配，对不上的引用会被直接丢弃；如果全部对不上，结论会被降级为「无风险」。

引用尽量短而精准，够说明问题即可（一般几个字到一句话）。
`offset` 字段留空或填 -1 即可，服务端会自己算。

**约束三：不确定就降级，不要硬判。**
把握不足时给低 `confidence`，或者干脆判「无风险」并在 `reasoning` 里说明
「无充分依据」。宁可漏判交给人工复核，也不要编造引用凑一个结论出来。

# 置信度

`confidence` 是 0~1 的小数，表示你对自己结论的把握。
有明确原文引用支撑时才给 0.8 以上；只是倾向性判断就给 0.5 左右。
"""

SYSTEM_PROMPT = _SYSTEM_PROMPT_TEMPLATE.replace("{{schema_text}}", _SCHEMA_TEXT)

SCREEN_SYSTEM_PROMPT = """你是社区风控的粗筛器。只判断这条帖要不要交给更强模型精判。
这是平台对公开内容履行治理义务的常规流程。

# 判定

- clean：明显是正常讨论、求助、分享，没有广告引流、辱骂、色情、侵权、引战、违法的迹象
- suspicious：有任何可疑点，或你没把握。宁可错送给精判，也不要放掉违规

# 输出

**调用 `submit_screen_verdict` 工具提交结论**，这是主要方式。

没能成功调用工具时，退而求其次：在正文里直接输出一个同样结构的 JSON 对象，
不要 markdown：

{"verdict":"clean 或 suspicious","confidence":0到1的小数,"reason":"一句话"}

不确定就输出 suspicious，confidence 低于 0.7。
"""

_USER_PROMPT_TEMPLATE = """请审核下面这条帖子。

<post_title>
{{title}}
</post_title>

<post_content>
{{content}}
</post_content>
"""


def _render(value: PromptValue, **variables: str) -> PromptValue:
    content = value.content
    for name, variable in variables.items():
        content = content.replace("{{" + name + "}}", variable)
    return replace(value, content=content)


def load_judge_system_prompt() -> PromptValue:
    value = get_prompt(SYSTEM_PROMPT_KEY, _SYSTEM_PROMPT_TEMPLATE)
    return _render(value, schema_text=_SCHEMA_TEXT)


def load_screen_system_prompt() -> PromptValue:
    return get_prompt(SCREEN_SYSTEM_PROMPT_KEY, SCREEN_SYSTEM_PROMPT)


def load_user_prompt(title: str, content: str) -> PromptValue:
    """读取并渲染用户提示词；XML 标签降低正文对提示词边界的干扰。"""
    value = get_prompt(USER_PROMPT_KEY, _USER_PROMPT_TEMPLATE)
    return _render(value, title=title, content=content)


def build_user_prompt(title: str, content: str) -> str:
    """兼容旧调用方，只返回渲染后的提示词正文。"""
    return load_user_prompt(title, content).content
