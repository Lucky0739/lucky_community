package com.lucky.enums;

import lombok.Getter;

/**
 * 角色身份枚举（公共模块，code 与 tb_role.role_code 对应）
 * <p>VISITOR 为游客(未登录、无 db 记录)，不算真实身份；其余三种为落库角色。
 */
@Getter
public enum RoleType {

    /** 游客（无 db 记录，不算身份） */
    VISITOR("VISITOR"),

    /** 普通吧友（用户注册默认） */
    NORMAL_USER("NORMAL_USER"),

    /** 本吧吧务（由大吧/平台管理员任命） */
    BAR_ADMIN("BAR_ADMIN"),

    /** 平台官方管理员（系统后台新增，允许多个） */
    PLATFORM_ADMIN("PLATFORM_ADMIN");

    /** 角色编码(tb_role.role_code) */
    private final String code;

    RoleType(String code) {
        this.code = code;
    }
}
