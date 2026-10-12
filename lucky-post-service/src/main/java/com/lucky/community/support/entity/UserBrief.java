package com.lucky.community.support.entity;

import lombok.Data;

/**
 * 查询吧返回的用户类
 */
@Data
public class UserBrief {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 用户头像
     */
    private String icon;
}
