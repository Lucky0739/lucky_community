package com.lucky.core.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lucky.common.enums.RoleType;
import com.lucky.core.auth.LoginAttemptService;
import com.lucky.core.auth.TokenIssuer;
import com.lucky.core.auth.dto.TokenDTO;
import com.lucky.core.user.dto.AppointBarAdminDTO;
import com.lucky.core.user.dto.LoginDTO;
import com.lucky.core.user.dto.RegisterDTO;
import com.lucky.core.user.dto.UpdateProfileDTO;
import com.lucky.core.bar.entity.BarManager;
import com.lucky.core.user.entity.Role;
import com.lucky.core.user.entity.User;
import com.lucky.core.user.entity.UserInfo;
import com.lucky.core.user.entity.UserPhone;
import com.lucky.core.user.entity.UserRole;
import com.lucky.core.bar.mapper.BarManagerMapper;
import com.lucky.core.user.mapper.RoleMapper;
import com.lucky.core.user.mapper.UserInfoMapper;
import com.lucky.core.user.mapper.UserMapper;
import com.lucky.core.user.mapper.UserPhoneMapper;
import com.lucky.core.user.mapper.UserRoleMapper;
import com.lucky.core.user.service.PermissionService;
import com.lucky.core.user.service.UserService;
import com.lucky.core.user.vo.LoginVO;
import com.lucky.core.user.vo.RoleVO;
import com.lucky.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final PermissionService permissionService;
    private final UserInfoMapper userInfoMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final BarManagerMapper barManagerMapper;

    @Override
    // 注册跨 4 张表，加事务保证要么全成功、要么全回滚
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        // 1. 先查手机号是否已存在(拦掉绝大多数重复提交)
        boolean exists = lambdaQuery().eq(User::getPhone, registerDTO.getPhone()).exists();
        if (exists) {
            throw new BusinessException("该手机号已注册");
        }
        // 2. 密码以 Bcrypt 加密存储；昵称默认使用用户id（先取雪花id再落库）
        Long id = IdWorker.getId();
        User user = new User();
        user.setId(id);
        user.setPhone(registerDTO.getPhone());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickName(String.valueOf(id));
        // 并发兜底：万一两个请求同时通过了上面的查重，靠 tb_user.phone 唯一索引拦下第二个
        try {
            save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该手机号已注册");
        }

        // 3. 用户扩展信息：粉丝数/关注数初始为 0
        UserInfo userInfo = new UserInfo();
        userInfo.setId(IdWorker.getId());
        userInfo.setUserId(id);
        userInfo.setFans(0);
        userInfo.setFollowee(0);
        userInfoMapper.insert(userInfo);

        // 4. 手机号记录
        UserPhone userPhone = new UserPhone();
        userPhone.setId(IdWorker.getId());
        userPhone.setUserId(id);
        userPhone.setPhone(registerDTO.getPhone());
        userPhoneMapper.insert(userPhone);

        // 5. 默认绑定普通吧友角色：按 role_code 查，避免角色 id 调整后误绑高权限角色
        Role defaultRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, RoleType.NORMAL_USER.getCode()));
        if (defaultRole == null) {
            throw new BusinessException("默认角色未配置");
        }
        UserRole userRole = new UserRole();
        userRole.setId(IdWorker.getId());
        userRole.setUserId(id);
        userRole.setRoleId(defaultRole.getId());
        userRoleMapper.insert(userRole);
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 0. 防刷：失败次数超限直接拒绝(在查库/验密之前拦截，顺带省掉 BCrypt 开销)
        if (loginAttemptService.isBlocked(loginDTO.getPhone())) {
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }
        // 1. 按手机号查用户
        User user = lambdaQuery().eq(User::getPhone, loginDTO.getPhone()).one();
        // 账号不存在与密码错误统一提示，避免暴露用户是否存在
        // 密码以 Bcrypt 存储，用 matches 比对
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(loginDTO.getPhone());
            throw new BusinessException("账号或密码错误");
        }
        // 登录成功：清空失败计数
        loginAttemptService.clear(loginDTO.getPhone());
        // 2. 签发双 Token 并登记会话状态(短 token 返回前端，长 token 由 Controller 写 Cookie)
        TokenDTO tokens = tokenIssuer.issuePair(user.getId()).tokens();
        // 3. 查用户全部角色，角色身份随登录一并返回(前端据此展示/路由)
        List<Role> roles = permissionService.listRoles(user.getId());
        // 4. 组装登录返回体
        LoginVO loginvo = new LoginVO();
        loginvo.setUserId(user.getId());
        loginvo.setPhone(user.getPhone());
        loginvo.setNickName(user.getNickName());
        loginvo.setIcon(user.getIcon());
        loginvo.setAccessToken(tokens.getAccessToken());
        loginvo.setRefreshToken(tokens.getRefreshToken());
        loginvo.setRoles(roles.stream()
                .map(r -> new RoleVO(r.getId(), r.getRoleCode(), r.getRoleName()))
                .toList());
        return loginvo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appointBarAdmin(Long grantUserId, AppointBarAdminDTO dto) {
        // 1. 校验任命人必须是平台管理员
        if (!permissionService.isPlatformAdmin(grantUserId)) {
            throw new BusinessException("仅平台管理员可任命吧务");
        }
        // 2. 校验被任命用户存在
        if (getById(dto.getUserId()) == null) {
            throw new BusinessException("被任命用户不存在");
        }
        // 3. 写入任命记录(uk_bar_user 唯一索引兜底：同一用户在同一个吧最多一条吧务身份)
        BarManager barManager = new BarManager();
        barManager.setBarId(dto.getBarId());
        barManager.setUserId(dto.getUserId());
        barManager.setGrantUserId(grantUserId);
        barManager.setStatus(BarManager.STATUS_ACTIVE);
        try {
            barManagerMapper.insert(barManager);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该用户已是本吧吧务");
        }
        // 4. 给目标用户绑定 BAR_ADMIN 角色(幂等：已有则跳过)
        permissionService.bindRoleIfAbsent(dto.getUserId(), RoleType.BAR_ADMIN);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UpdateProfileDTO dto) {
        // 1. 更新 tb_user：昵称/头像（非空才更新）
        if (dto.getNickName() != null || dto.getIcon() != null) {
            LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
            uw.eq(User::getId, userId);
            if (dto.getNickName() != null) {
                uw.set(User::getNickName, dto.getNickName());
            }
            if (dto.getIcon() != null) {
                uw.set(User::getIcon, dto.getIcon());
            }
            update(uw);
        }
        // 2. 更新 tb_user_info：城市/介绍/性别/生日（非空才更新）
        if (dto.getCity() != null || dto.getIntroduce() != null
                || dto.getGender() != null || dto.getBirthday() != null) {
            LambdaUpdateWrapper<UserInfo> uw = new LambdaUpdateWrapper<>();
            uw.eq(UserInfo::getUserId, userId);
            if (dto.getCity() != null) {
                uw.set(UserInfo::getCity, dto.getCity());
            }
            if (dto.getIntroduce() != null) {
                uw.set(UserInfo::getIntroduce, dto.getIntroduce());
            }
            if (dto.getGender() != null) {
                uw.set(UserInfo::getGender, dto.getGender());
            }
            if (dto.getBirthday() != null) {
                uw.set(UserInfo::getBirthday, dto.getBirthday());
            }
            userInfoMapper.update(null, uw);
        }
    }
}
