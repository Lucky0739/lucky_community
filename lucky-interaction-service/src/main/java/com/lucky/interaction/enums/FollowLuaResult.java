package com.lucky.interaction.enums;

import lombok.Getter;

/**
 * 关注 Lua 的返回码。数字必须和 {@code lua/follow.lua} 一致。
 */
@Getter
public enum FollowLuaResult {

    /** 写入关注关系且关注数 +1 */
    SUCCESS(0L, null),

    /** 已经关注过该用户 */
    ALREADY(1L, "已经关注该用户"),

    /** 不能关注自己 */
    SELF(2L, "不能关注自己"),

    /** 关注人数已达上限 */
    LIMIT(3L, "关注人数不能超过2000人");

    private final long code;
    private final String errorMsg;

    FollowLuaResult(long code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public static FollowLuaResult fromCode(Long code) {
        if (code == null) {
            return null;
        }
        for (FollowLuaResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
