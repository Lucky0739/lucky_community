package com.lucky.interaction.enums;

import lombok.Getter;

@Getter
public enum FavoriteUnfavoriteLuaResult {

    BLOOM_ABSENT(-1L, "取消收藏失败，请稍后重试"),
    SUCCESS(0L, null),
    NOT_FAVORITED(1L, "尚未收藏该帖子"),
    NEED_DB(2L, null),
    CONFLICT(3L, "取消收藏失败，请稍后重试");

    private final long code;
    private final String errorMsg;

    FavoriteUnfavoriteLuaResult(long code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public static FavoriteUnfavoriteLuaResult fromCode(Long code) {
        if (code == null) {
            return null;
        }
        for (FavoriteUnfavoriteLuaResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
