package com.lucky.interaction.enums;

import lombok.Getter;

/**
 * 取消关注 Lua 的返回码。数字必须和 {@code lua/unfollow.lua} 一致。
 */
@Getter
public enum UnfollowLuaResult {

    /** 已从 ZSet 删除且关注数 -1 */
    SUCCESS(0L, null),

    /** 尚未关注该用户 */
    NOT_FOLLOWED(1L, "尚未关注该用户");

    private final long code;
    private final String errorMsg;

    UnfollowLuaResult(long code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public static UnfollowLuaResult fromCode(Long code) {
        if (code == null) {
            return null;
        }
        for (UnfollowLuaResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
