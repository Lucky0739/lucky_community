package com.lucky.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联表
 */
@Data
public class UserRole {

    /** 主键ID */
    private Long id;

    /** 用户ID，关联tb_user.id */
    private Long userId;

    /** 角色ID，关联tb_role.id */
    private Long roleId;

    /** 逻辑删除：0未删除，1已删除 */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
