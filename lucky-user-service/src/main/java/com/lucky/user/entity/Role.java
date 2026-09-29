package com.lucky.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色表
 */
@Data
public class Role {

    /** 角色主键ID */
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 角色编码(程序判断使用) */
    private String roleCode;

    /** 角色说明 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
