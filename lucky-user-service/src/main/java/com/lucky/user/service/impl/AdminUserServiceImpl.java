package com.lucky.user.service.impl;

import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
import com.lucky.user.auth.AuthService;
import com.lucky.user.dto.AdminUpdateProfileDTO;
import com.lucky.user.dto.AdminUserQueryDTO;
import com.lucky.user.dto.AppointBarAdminDTO;
import com.lucky.user.entity.BarManager;
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
import com.lucky.common.vo.PageResult;
import com.lucky.user.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
    private final PermissionService permissionService;
    private final UserService userService;
    private final AuthService authService;

    /**
     * 分页查询用户
     * @param operatorId 操作人(当前登录的平台管理员)
     * @param adminUserQueryDTO      筛选条件，phone 前缀匹配 / nickName 模糊匹配
     * @return
     */
    @Override
    public PageResult<AdminUserVO> pageUsers(Long operatorId, AdminUserQueryDTO adminUserQueryDTO) {
        // 检验操作用户是否管理员身份
        requirePlatformAdmin(operatorId);
        String phone = StringUtils.hasText(adminUserQueryDTO.getPhone()) ? adminUserQueryDTO.getPhone() : null;
        String nickName = StringUtils.hasText(adminUserQueryDTO.getNickName()) ? adminUserQueryDTO.getNickName() : null;
        Integer pageNum = adminUserQueryDTO.getPageNum();
        if(pageNum == null){
            pageNum=1;
        }
        Integer pageSize = adminUserQueryDTO.getPageSize();
        if(pageSize == null){
            pageSize=10;
        }
        int offset = (pageNum - 1) * pageSize;
        // 查询用户数量
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

    /**
     * 查询用户详情
     * @param operatorId
     * @param userId
     * @return
     */
    @Override
    public AdminUserDetailVO getUserDetail(Long operatorId, Long userId) {
        // 检验是否平台管理员
        requirePlatformAdmin(operatorId);
        // 查询对应用户
        User user = requireActiveUser(userId);
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        AdminUserDetailVO adminUserDetailVO = new AdminUserDetailVO();
        adminUserDetailVO.setUserId(user.getId());
        adminUserDetailVO.setPhone(user.getPhone());
        adminUserDetailVO.setNickName(user.getNickName());
        adminUserDetailVO.setIcon(user.getIcon());
        adminUserDetailVO.setCreateTime(user.getCreateTime());
        adminUserDetailVO.setUpdateTime(user.getUpdateTime());
        adminUserDetailVO.setRoles(toRoleVOs(permissionService.listRoles(userId)));
        if (userInfo != null) {
            adminUserDetailVO.setCity(userInfo.getCity());
            adminUserDetailVO.setIntroduce(userInfo.getIntroduce());
            adminUserDetailVO.setFans(userInfo.getFans());
            adminUserDetailVO.setFollowee(userInfo.getFollowee());
            adminUserDetailVO.setGender(userInfo.getGender());
            adminUserDetailVO.setBirthday(userInfo.getBirthday());
        }
        return adminUserDetailVO;
    }

    /**
     * 修改更新用户信息：改的是 dto.getUserId() 指向的用户，不是操作人自己
     * @param operatorId 操作人(当前登录的平台管理员)
     */
    @Override
    public void updateUser(Long operatorId, AdminUpdateProfileDTO updateProfileDTO) {
        requirePlatformAdmin(operatorId);
        // 获取被编辑人id
        Long userId = updateProfileDTO.getUserId();
        // 检验userId是否为空
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        // 根据userId查出来的未删除的用户
        System.out.println("========"+userMapper.selectById(userId));
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        userService.updateProfileForUser(userId, updateProfileDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appointBarAdmin(Long operatorId, AppointBarAdminDTO dto) {
        requirePlatformAdmin(operatorId);
        if (userMapper.selectById(dto.getUserId()) == null) {
            throw new BusinessException("被任命用户不存在");
        }
        BarManager barManager = new BarManager();
        barManager.setBarId(dto.getBarId());
        barManager.setUserId(dto.getUserId());
        barManager.setGrantUserId(operatorId);
        barManager.setStatus(BarManager.STATUS_ACTIVE);
        try {
            barManagerMapper.insert(barManager);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该用户已是本吧吧务");
        }
        permissionService.bindRoleIfAbsent(dto.getUserId(), RoleType.BAR_ADMIN);
    }

    /**
     * 删除用户
     * @param operatorId
     * @param userIds 待删除的用户ID，非空
     */
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

    /**
     * 删除用户的检验逻辑方法
     * @param operatorId
     * @param userIds
     */
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

    /**
     * 删除用户逻辑
     * @param userId
     */
    private void applyDelete(Long userId) {
        // 吧该用户对应的吧撤销
        int revoked = barManagerMapper.revokeByUserId(userId);
        if (revoked > 0) {
            log.warn("删除用户时级联撤销吧务任命，userId={}，影响 {} 条", userId, revoked);
        }
        // 清空被删除用户对应的电话号码并设置逻辑删除
        int rows = userMapper.logicalDeleteAndReleasePhone(userId);
        if (rows == 0) {
            throw new BusinessException("用户不存在");
        }
        // 逻辑删除对应的角色用户表
        userRoleMapper.logicalDeleteByUserId(userId);
        authService.revoke(userId);
    }

    private void requirePlatformAdmin(Long operatorId) {
        if (!permissionService.isPlatformAdmin(operatorId)) {
            throw new BusinessException("仅平台管理员可访问");
        }
    }

    // 查询用户表
    private User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private AdminUserVO toVO(User user, List<Role> roles) {
        AdminUserVO adminUserVO = new AdminUserVO();
        adminUserVO.setUserId(user.getId());
        adminUserVO.setPhone(user.getPhone());
        adminUserVO.setNickName(user.getNickName());
        adminUserVO.setIcon(user.getIcon());
        adminUserVO.setCreateTime(user.getCreateTime());
        adminUserVO.setUpdateTime(user.getUpdateTime());
        adminUserVO.setRoles(toRoleVOs(roles));
        return adminUserVO;
    }

    private List<RoleVO> toRoleVOs(List<Role> roles) {
        return roles.stream()
                .map(role -> new RoleVO(role.getId(), role.getRoleCode(), role.getRoleName()))
                .toList();
    }
}
