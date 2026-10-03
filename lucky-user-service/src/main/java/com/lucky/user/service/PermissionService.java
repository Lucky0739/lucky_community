package com.lucky.user.service;

import com.lucky.common.enums.RoleType;
import com.lucky.user.entity.Role;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色查询与鉴权：用户角色的唯一入口
 */
public interface PermissionService {

    /**
     * 查用户全部角色（tb_user_role 关联 tb_role）
     */
    List<Role> listRoles(Long userId);

    /**
     * 批量查角色：userId -> 角色列表
     */
    Map<Long, List<Role>> listRolesByUserIds(Collection<Long> userIds);

    /**
     * 是否拥有平台管理员角色：鉴权的唯一方法
     */
    boolean isPlatformAdmin(Long userId);

    /**
     * 从一批用户里挑出拥有平台管理员角色的
     */
    Set<Long> filterPlatformAdmins(Collection<Long> userIds);

    /**
     * 给用户绑定一个角色
     */
    void bindRoleIfAbsent(Long userId, RoleType roleType);
}
