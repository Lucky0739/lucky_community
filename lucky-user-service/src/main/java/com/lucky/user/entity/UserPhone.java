package com.lucky.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户手机表
 */
@Data
public class UserPhone {

    /** 主键id */
    private Long id;

    /** 用户id */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
