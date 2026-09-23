package com.lucky.core.user.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员视角的用户列表行
 * <p>刻意不直接返回 {@code User} 实体：实体带 password(BCrypt 哈希)，不能出进程
 */
@Data
public class AdminUserVO {

    /** 用户ID */
    private Long userId;

    /** 手机号码（已注销的用户为 null） */
    private String phone;

    /** 昵称 */
    private String nickName;

    /** 人物头像 */
    private String icon;

    /** 角色列表 */
    private List<RoleVO> roles;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
