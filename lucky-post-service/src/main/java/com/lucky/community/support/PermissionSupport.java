package com.lucky.community.support;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
import com.lucky.community.support.entity.RoleRef;
import com.lucky.community.support.entity.UserRoleRef;
import com.lucky.community.support.mapper.RoleMapper;
import com.lucky.community.support.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionSupport {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public boolean isPlatformAdmin(Long userId) {
        return userRoleMapper.existsByUserIdAndRoleCode(userId, RoleType.PLATFORM_ADMIN.getCode());
    }

    public void bindRoleIfAbsent(Long userId, RoleType roleType) {
        RoleRef role = roleMapper.selectByRoleCode(roleType.getCode());
        if (role == null) {
            throw new BusinessException("角色未配置：" + roleType.getCode());
        }
        if (userRoleMapper.existsByUserIdAndRoleId(userId, role.getId())) {
            return;
        }
        UserRoleRef userRole = new UserRoleRef();
        userRole.setId(IdUtil.getSnowflakeNextId());
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }
}
