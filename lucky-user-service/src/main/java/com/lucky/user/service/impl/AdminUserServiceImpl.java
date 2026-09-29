package com.lucky.user.service.impl;

import com.lucky.common.exception.BusinessException;
import com.lucky.user.auth.AuthService;
import com.lucky.user.dto.AdminUserQueryDTO;
import com.lucky.user.dto.HandlerActionDTO;
import com.lucky.user.dto.UpdateProfileDTO;
import com.lucky.user.entity.Role;
import com.lucky.user.entity.User;
import com.lucky.user.entity.UserInfo;
import com.lucky.user.mapper.BarManagerMapper;
import com.lucky.user.mapper.UserInfoMapper;
import com.lucky.user.mapper.UserMapper;
import com.lucky.user.mapper.UserRoleMapper;
import com.lucky.user.service.AdminUserService;
import com.lucky.user.service.PermissionService;
import com.lucky.user.service.UserService;
import com.lucky.user.vo.AdminUserDetailVO;
import com.lucky.user.vo.AdminUserVO;
import com.lucky.user.vo.PageResult;
import com.lucky.user.vo.RoleVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员用户管理实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final BarManagerMapper barManagerMapper;
    private final UserRoleMapper userRoleMapper;

    @Resource
    private PermissionService permissionService;

    @Resource
    private UserService userService;

    @Resource
    private AuthService authService;

    @Override
    public PageResult<AdminUserVO> pageUsers(Long operatorId, AdminUserQueryDTO query) {
        requirePlatformAdmin(operatorId);
        String phone = StringUtils.hasText(query.getPhone()) ? query.getPhone() : null;
        String nickName = StringUtils.hasText(query.getNickName()) ? query.getNickName() : null;
        long pageNum = query.getPageNum() == null ? 1L : query.getPageNum();
        long pageSize = query.getPageSize() == null ? 10L : query.getPageSize();
        long offset = (pageNum - 1) * pageSize;
        long total = userMapper.countByFilter(phone, nickName);
        if (total == 0) {
            return new PageResult<>(List.of(), 0);
        }
        List<User> users = userMapper.selectPageByFilter(phone, nickName, offset, pageSize);
        if (users.isEmpty()) {
            return new PageResult<>(List.of(), total);
        }
        Map<Long, List<Role>> rolesByUserId = permissionService.listRolesByUserIds(
                users.stream().map(User::getId).toList());
        List<AdminUserVO> records = users.stream()
                .map(user -> toVO(user, rolesByUserId.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResult<>(records, total);
    }

    @Override
    public AdminUserDetailVO getUserDetail(Long operatorId, Long userId) {
        requirePlatformAdmin(operatorId);
        User user = requireActiveUser(userId);
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        AdminUserDetailVO vo = new AdminUserDetailVO();
        vo.setUserId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setNickName(user.getNickName());
        vo.setIcon(user.getIcon());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setRoles(toRoleVOs(permissionService.listRoles(userId)));
        if (userInfo != null) {
            vo.setCity(userInfo.getCity());
            vo.setIntroduce(userInfo.getIntroduce());
            vo.setFans(userInfo.getFans());
            vo.setFollowee(userInfo.getFollowee());
            vo.setGender(userInfo.getGender());
            vo.setBirthday(userInfo.getBirthday());
        }
        return vo;
    }

    @Override
    public void updateUser(Long operatorId, UpdateProfileDTO dto) {
        requirePlatformAdmin(operatorId);
        Long userId = dto.getUserId();
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        userService.updateProfile(userId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUsers(Long operatorId, List<Long> userIds) {
        requirePlatformAdmin(operatorId);
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("请选择要删除的用户");
        }
        List<Long> ids = List.copyOf(new LinkedHashSet<>(userIds));
        validateDeletable(operatorId, ids);
        ids.forEach(this::applyDelete);
    }

    @Override
    public void reviewPost(Long operatorId, HandlerActionDTO dto) {
        requirePlatformAdmin(operatorId);
        throw new BusinessException("帖子处置尚未迁入用户服务");
    }

    private void validateDeletable(Long operatorId, List<Long> userIds) {
        if (userIds.contains(operatorId)) {
            throw new BusinessException("不能删除自己");
        }
        Set<Long> alive = userMapper.selectByIds(userIds).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        Long missing = userIds.stream().filter(id -> !alive.contains(id)).findFirst().orElse(null);
        if (missing != null) {
            throw new BusinessException("用户 " + missing + " 不存在或已被删除");
        }
        Set<Long> admins = permissionService.filterPlatformAdmins(userIds);
        if (!admins.isEmpty()) {
            throw new BusinessException("用户 " + admins.iterator().next() + " 是平台管理员账号，不能删除");
        }
    }

    private void applyDelete(Long userId) {
        int revoked = barManagerMapper.revokeByUserId(userId);
        if (revoked > 0) {
            log.warn("删除用户时级联撤销吧务任命，userId={}，影响 {} 条", userId, revoked);
        }
        int rows = userMapper.logicalDeleteAndReleasePhone(userId);
        if (rows == 0) {
            throw new BusinessException("用户不存在");
        }
        userRoleMapper.deleteByUserId(userId);
        authService.revoke(userId);
    }

    private void requirePlatformAdmin(Long operatorId) {
        if (!permissionService.isPlatformAdmin(operatorId)) {
            throw new BusinessException("仅平台管理员可访问");
        }
    }

    private User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private AdminUserVO toVO(User user, List<Role> roles) {
        AdminUserVO vo = new AdminUserVO();
        vo.setUserId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setNickName(user.getNickName());
        vo.setIcon(user.getIcon());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setRoles(toRoleVOs(roles));
        return vo;
    }

    private List<RoleVO> toRoleVOs(List<Role> roles) {
        return roles.stream()
                .map(role -> new RoleVO(role.getId(), role.getRoleCode(), role.getRoleName()))
                .toList();
    }
}
