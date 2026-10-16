package com.lucky.interaction.enums;

import lombok.Getter;

@Getter
public enum FavoriteLuaResult {

    BLOOM_ABSENT(-1L, "收藏失败，请稍后重试"),
    SUCCESS(0L, null),
    ALREADY(1L, "已经收藏过该帖子");

    private final long code;
    private final String errorMsg;

    FavoriteLuaResult(long code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public static FavoriteLuaResult fromCode(Long code) {
        if (code == null) {
            return null;
        }
        for (FavoriteLuaResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
