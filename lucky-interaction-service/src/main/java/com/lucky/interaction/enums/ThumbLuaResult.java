package com.lucky.interaction.enums;

import lombok.Getter;

/**
 * 点赞 Lua 的返回码。数字必须和 {@code lua/thumb_like.lua} 一致。
 */
@Getter
public enum ThumbLuaResult {

    /** 布隆过滤器的 key 不存在，需要先按 tb_thumb 建好再重试 */
    BLOOM_ABSENT(-1L, "点赞失败，请稍后重试"),

    /** 未点赞，脚本已写入点赞数据并把帖子放进布隆 */
    SUCCESS(0L, null),

    /** 布隆与点赞数据都表明已经点过 */
    ALREADY(1L, "已经点赞过该帖子");

    private final long code;
    private final String errorMsg;

    ThumbLuaResult(long code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public static ThumbLuaResult fromCode(Long code) {
        if (code == null) {
            return null;
        }
        for (ThumbLuaResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
