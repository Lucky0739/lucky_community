"""兼容旧导入。敏感词已从「LLM 失败后的降级」改成漏斗第 ① 层，实现见 keywords.py。"""

from .keywords import assess

__all__ = ["assess"]
