package com.lucky.community.entity;

import java.util.Arrays;

/**
 * 吧务 / 平台管理员对一条帖子的最终处置。
 */
public enum HandlerAction {

    /** 看过，决定不处理 */
    NONE("未处置"),

    /** 警告，帖子保留 */
    WARN("警告"),

    /** 逻辑删除帖子 */
    DELETE_POST("删帖"),

    /** 逻辑删除帖子，并封禁作者账号 */
    BAN("封禁");

    private final String value;

    HandlerAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 删帖、封禁都要把帖子从列表里拿掉 */
    public boolean deletesPost() {
        return this == DELETE_POST || this == BAN;
    }

    public static HandlerAction from(String raw) {
        if (raw == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(action -> action.value.equals(raw))
                .findFirst()
                .orElse(null);
    }
}
