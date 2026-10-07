package com.lucky.interaction.enums;

import lombok.Getter;

/**
 * 取消点赞 Lua 的返回码。数字必须和 {@code lua/thumb_unlike.lua} 一致。
 */
@Getter
public enum ThumbUnlikeLuaResult {

    /** 布隆过滤器的 key 不存在，需要先按 tb_thumb 建好再重试 */
    BLOOM_ABSENT(-1L, "取消点赞失败，请稍后重试"),

    /** 已从点赞 Hash 删除，计数已 -1，帖子已放入取消集合 */
    SUCCESS(0L, null),

    /** 布隆和点赞数据都表明没有点过 */
    NOT_LIKED(1L, "尚未点赞该帖子"),

    /** 布隆命中但 Hash 没有这条，需要用 tb_thumb 确认后再取消 */
    NEED_DB(2L, null),

    /** 查库确认期间用户又点了赞，这次不能删掉新的点赞 */
    CONFLICT(3L, "取消点赞失败，请稍后重试");

    private final long code;
    private final String errorMsg;

    ThumbUnlikeLuaResult(long code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public static ThumbUnlikeLuaResult fromCode(Long code) {
        if (code == null) {
            return null;
        }
        for (ThumbUnlikeLuaResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
