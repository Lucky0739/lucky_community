package com.lucky.core.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.lucky.core.user.entity.Role;
import com.lucky.core.user.entity.UserRole;
import com.lucky.core.user.mapper.RoleMapper;
import com.lucky.core.user.mapper.UserRoleMapper;
import com.lucky.core.user.service.PermissionService;
import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    @Override
    public List<Role> listRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        // 去重后再查：同一用户理论上不会重复绑同一角色(uk_user_role 兜底)，这里只是防御
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        return roleMapper.selectBatchIds(roleIds);
    }

    @Override
    public Map<Long, List<Role>> listRolesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));
        if (userRoles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        // 先按角色 id 建索引再组装，避免逐个用户查库
        Map<Long, Role> roleById = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<Long, List<Role>> rolesByUserId = new HashMap<>();
        for (UserRole userRole : userRoles) {
            Role role = roleById.get(userRole.getRoleId());
            // 角色被删而绑定残留时跳过，不让列表接口整个失败
            if (role != null) {
                rolesByUserId.computeIfAbsent(userRole.getUserId(), k -> new ArrayList<>()).add(role);
            }
        }
        return rolesByUserId;
    }

    @Override
    public boolean isPlatformAdmin(Long userId) {
        return userRoleMapper.existsByUserIdAndRoleCode(userId, RoleType.PLATFORM_ADMIN.getCode());
    }

    @Override
    public void bindRoleIfAbsent(Long userId, RoleType roleType) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, roleType.getCode()));
        if (role == null) {
            throw new BusinessException("角色未配置：" + roleType.getCode());
        }
        boolean exists = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId())) > 0;
        if (exists) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setId(IdWorker.getId());
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    @Override
    public Set<Long> filterPlatformAdmins(Collection<Long> userIds) {
        return listRolesByUserIds(userIds).entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(r -> RoleType.PLATFORM_ADMIN.getCode().equals(r.getRoleCode())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
