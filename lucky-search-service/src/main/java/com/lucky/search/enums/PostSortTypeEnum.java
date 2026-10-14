package com.lucky.search.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 帖子搜索排序类型。
 */
@Getter
@AllArgsConstructor
public enum PostSortTypeEnum {

    /** 最新 */
    LATEST(0),
    /** 最多点赞 */
    MOST_LIKE(1),
    /** 最多评论 */
    MOST_COMMENT(2),
    /** 最多收藏 */
    MOST_COLLECT(3),
    ;

    private final Integer code;

    /**
     * 根据类型 code 获取对应的枚举。没有匹配时返回 null。
     */
    public static PostSortTypeEnum valueOf(Integer code) {
        for (PostSortTypeEnum sortType : PostSortTypeEnum.values()) {
            if (Objects.equals(code, sortType.getCode())) {
                return sortType;
            }
        }
        return null;
    }
}
