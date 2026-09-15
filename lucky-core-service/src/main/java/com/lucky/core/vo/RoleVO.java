package com.lucky.core.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色返回体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleVO {

    /** 角色主键 */
    private Long roleId;

    /** 角色编码（程序判断使用，如 USER/MERCHANT/ADMIN） */
    private String roleCode;

    /** 角色名称（展示使用，如 普通用户/商家/管理员） */
    private String roleName;
}
