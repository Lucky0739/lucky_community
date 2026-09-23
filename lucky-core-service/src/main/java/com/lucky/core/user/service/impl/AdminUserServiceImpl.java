package com.lucky.core.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lucky.core.user.dto.AdminUserQueryDTO;
import com.lucky.core.user.dto.UpdateProfileDTO;
import com.lucky.core.bar.entity.BarManager;
import com.lucky.core.user.entity.Role;
import com.lucky.core.user.entity.User;
import com.lucky.core.user.entity.UserInfo;
import com.lucky.core.user.entity.UserRole;
import com.lucky.core.bar.mapper.BarManagerMapper;
import com.lucky.core.user.mapper.UserInfoMapper;
import com.lucky.core.user.mapper.UserMapper;
import com.lucky.core.user.mapper.UserRoleMapper;
import com.lucky.core.auth.AuthService;
import com.lucky.core.user.service.AdminUserService;
import com.lucky.core.user.service.PermissionService;
import com.lucky.core.user.service.UserService;
import com.lucky.core.user.vo.AdminUserDetailVO;
import com.lucky.core.user.vo.AdminUserVO;
import com.lucky.core.user.vo.RoleVO;
import com.lucky.common.exception.BusinessException;
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

    /**
     * 分页查询
     * @param operatorId 操作人(当前登录的平台管理员)
     * @param query      筛选条件，phone 前缀匹配 / nickName 模糊匹配
     * @return
     */
    @Override
    public IPage<AdminUserVO> pageUsers(Long operatorId, AdminUserQueryDTO query) {
        requirePlatformAdmin(operatorId);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getId);
        if (StringUtils.hasText(query.getPhone())) {
            // 前缀匹配才能走 uniqe_key_phone 索引
            wrapper.likeRight(User::getPhone, query.getPhone());
        }
        if (StringUtils.hasText(query.getNickName())) {
            wrapper.like(User::getNickName, query.getNickName());
        }
        Page<User> page = userMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Page<AdminUserVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            // 本页无数据时跳过角色查询，省掉两次无用往返
            return result;
        }
        // 批量取角色后内存组装，避免逐个用户查库(N+1)
        Map<Long, List<Role>> rolesByUserId = permissionService.listRolesByUserIds(
                page.getRecords().stream().map(User::getId).toList());
        result.setRecords(page.getRecords().stream()
                .map(user -> toVO(user, rolesByUserId.getOrDefault(user.getId(), List.of())))
                .toList());
        return result;
    }

    /**
     查看用户详情信息
     */
    @Override
    public AdminUserDetailVO getUserDetail(Long operatorId, Long userId) {
        requirePlatformAdmin(operatorId);
        User user = requireActiveUser(userId);
        // tb_user_info 按 uk_user_id 唯一，正常一人一条；注册时即写入，缺失属异常数据
        UserInfo userInfo = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUserId, userId));
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

    /**
     * 修改用户资料
     * <p>目标用户来自请求体的 {@code userId}，操作者来自 token，两者不可混用——权限只校验 token 里的那个
     *
     * @param operatorId 操作人(当前登录的平台管理员)
     * @param dto        目标用户取 {@code dto.getUserId()}
     */
    @Override
    public void updateUser(Long operatorId, UpdateProfileDTO dto) {
        requirePlatformAdmin(operatorId);
        Long userId = dto.getUserId();
        // 共用的 DTO 上没法标 @NotNull(自助路径不传该字段)，只能在这里兜
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        // 由 @TableLogic 自动追加 AND deleted=0，已删用户查不到
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
        // 去重：同一个 id 传两遍，第二遍会撞「已被删除」，整批被它连累着回滚
        List<Long> ids = List.copyOf(new LinkedHashSet<>(userIds));

        // 先整批校验、再整批执行，而不是「边校验边删」。因为 applyDelete 末尾的 revoke
        // 写的是 Redis，事务回滚撤不回它——前几个已经被踢下线并打了墓碑，最后一个才失败，
        // 那几个就白踢了。两段式也顺带让校验可以用两条查询覆盖整批，而不是逐个用户打库
        validateDeletable(operatorId, ids);
        ids.forEach(this::applyDelete);
    }

    /**
     * 整批校验：任一条不过就抛异常，由调用方的事务回滚整批
     * <p>提示语一律带上 userId——批量场景下「不能删除平台管理员账号」不说是哪个，管理员无从下手
     */
    private void validateDeletable(Long operatorId, List<Long> userIds) {
        if (userIds.contains(operatorId)) {
            throw new BusinessException("不能删除自己");
        }
        // 一条 IN 查回整批：@TableLogic 会自动追加 AND deleted=0，已删用户查不到，
        // 所以「查回来的条数对不上」就等于其中有已被删除的
        Set<Long> alive = userMapper.selectBatchIds(userIds).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        Long missing = userIds.stream().filter(id -> !alive.contains(id)).findFirst().orElse(null);
        if (missing != null) {
            throw new BusinessException("用户 " + missing + " 不存在或已被删除");
        }
        // 禁止删除平台管理员：同时挡住管理员互删和「最后一个管理员删自己」导致无人可管。
        // 比统计管理员数量更简单，且没有并发竞态
        Set<Long> admins = permissionService.filterPlatformAdmins(userIds);
        if (!admins.isEmpty()) {
            throw new BusinessException("用户 " + admins.iterator().next() + " 是平台管理员账号，不能删除");
        }
    }

    /**
     * 执行单个用户的删除动作。调用前必须已通过 {@link #validateDeletable}
     * <p>前三步都是 DB，最后一步是 Redis。撤销放最后且必须是 {@code revoke} 而不是 {@code kickOut}：
     * kickOut 只删会话键，而签发路径(issuePair/issueAccessOnly)会把它重新写回来
     */
    private void applyDelete(Long userId) {
        // 级联撤销吧务身份
        revokeBarManager(userId);
        // 逻辑删除 用户表
        logicalDeleteAndReleasePhone(userId);
        // 物理删除 用户-角色表
        deleteUserRoles(userId);
        authService.revoke(userId);
    }
    /**
     * 级联撤销吧务身份
     */
    private void revokeBarManager(Long userId) {
        int revoked = barManagerMapper.update(null, new LambdaUpdateWrapper<BarManager>()
                .eq(BarManager::getUserId, userId)
                .eq(BarManager::getStatus, BarManager.STATUS_ACTIVE)
                .set(BarManager::getStatus, BarManager.STATUS_REVOKED));
        if (revoked > 0) {
            log.warn("删除用户时级联撤销吧务任命，userId={}，影响 {} 条", userId, revoked);
        }
    }

    /**
     * 物理删除该用户的全部角色绑定
     */
    private void deleteUserRoles(Long userId) {
        LambdaQueryWrapper<UserRole> lambdaQueryWrapper = new LambdaQueryWrapper<UserRole>();
        lambdaQueryWrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(lambdaQueryWrapper);
    }

    /**
     * 逻辑删除并与释放手机号合并为一条 UPDATE
     */
    private void logicalDeleteAndReleasePhone(Long userId) {
        // AND deleted=0 由 MyBatis-Plus 依 @TableLogic 自动追加，不要手写 .eq(User::getDeleted, 0)
        int rows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getDeleted, 1)
                .set(User::getPhone, null));
        if (rows == 0) {
            // 并发下被另一个管理员抢先删除
            throw new BusinessException("用户不存在");
        }
    }

    /**
     * 断言操作人是平台管理员，否则拒绝
     */
    private void requirePlatformAdmin(Long operatorId) {
        if (!permissionService.isPlatformAdmin(operatorId)) {
            throw new BusinessException("仅平台管理员可访问");
        }
    }
    /**
     * 查未删除用户，查不到即抛「用户不存在」
     */
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
