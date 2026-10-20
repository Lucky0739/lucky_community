package com.lucky.interaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注列表里的一个贴吧。followed 表示当前登录用户是否关注了它。
 */
@Data
public class FollowBarVO {

    private Long barId;
    private String name;
    private String avatar;
    private Integer followCount;
    private Boolean followed;
    private LocalDateTime followTime;
}
