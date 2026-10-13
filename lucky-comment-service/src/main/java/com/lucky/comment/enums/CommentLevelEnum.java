package com.lucky.comment.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 评论级别
 */
@Getter
@AllArgsConstructor
public enum CommentLevelEnum {
    /** 一级评论 */
    ONE(1),
    /** 二级评论 */
    TWO(2),
    ;

    private final Integer code;

    public static CommentLevelEnum valueOf(Integer code) {
        for (CommentLevelEnum level : CommentLevelEnum.values()) {
            if (Objects.equals(code, level.getCode())) {
                return level;
            }
        }
        return null;
    }
}
