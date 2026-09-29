package com.lucky.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
import com.lucky.user.entity.Role;
import com.lucky.user.entity.UserRole;
import com.lucky.user.mapper.RoleMapper;
import com.lucky.user.mapper.UserRoleMapper;
import com.lucky.user.service.PermissionService;
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
        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        return roleMapper.selectByIds(roleIds);
    }

    @Override
    public Map<Long, List<Role>> listRolesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRole> userRoles = userRoleMapper.selectByUserIds(List.copyOf(userIds));
        if (userRoles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        Map<Long, Role> roleById = roleMapper.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<Long, List<Role>> rolesByUserId = new HashMap<>();
        for (UserRole userRole : userRoles) {
            Role role = roleById.get(userRole.getRoleId());
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
        Role role = roleMapper.selectByRoleCode(roleType.getCode());
        if (role == null) {
            throw new BusinessException("角色未配置：" + roleType.getCode());
        }
        if (userRoleMapper.existsByUserIdAndRoleId(userId, role.getId())) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setId(IdUtil.getSnowflakeNextId());
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
