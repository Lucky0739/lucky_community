package com.lucky.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户手机表
 */
@Data
@TableName("tb_user_phone")
public class UserPhone {

    /** 主键id */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
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
