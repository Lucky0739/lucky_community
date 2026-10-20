package com.lucky.interaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注列表里的一个用户。followed 表示当前登录用户是否关注了他，用来渲染「已关注 / 取消关注」。
 */
@Data
public class FollowUserVO {

    private Long userId;
    private String nickName;
    private String icon;
    /** 当前登录用户是否已关注该用户 */
    private Boolean followed;
    private LocalDateTime followTime;
}
