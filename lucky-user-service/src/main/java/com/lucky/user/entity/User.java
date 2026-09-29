package com.lucky.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
public class User {

    /** 主键 */
    private Long id;

    /** 手机号码 */
    private String phone;

    /** 密码，加密存储 */
    private String password;

    /** 昵称，默认是用户id */
    private String nickName;

    /** 人物头像 */
    private String icon;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除：0未删除，1已删除 */
    private Integer deleted;
}
