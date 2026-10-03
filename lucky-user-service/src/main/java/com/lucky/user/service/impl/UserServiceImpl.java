package com.lucky.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.auth.UserContext;
import com.lucky.common.enums.RoleType;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.exception.UnauthorizedException;
import com.lucky.user.auth.LoginAttemptService;
import com.lucky.user.auth.TokenIssuer;
import com.lucky.user.auth.dto.TokenDTO;
import com.lucky.user.dto.LoginDTO;
import com.lucky.user.dto.RegisterDTO;
import com.lucky.user.dto.UpdateProfileDTO;
import com.lucky.user.entity.Role;
import com.lucky.user.entity.User;
import com.lucky.user.entity.UserInfo;
import com.lucky.user.entity.UserPhone;
import com.lucky.user.entity.UserRole;
import com.lucky.user.mapper.RoleMapper;
import com.lucky.user.mapper.UserInfoMapper;
import com.lucky.user.mapper.UserMapper;
import com.lucky.user.mapper.UserPhoneMapper;
import com.lucky.user.mapper.UserRoleMapper;
import com.lucky.user.service.PermissionService;
import com.lucky.user.service.UserService;
import com.lucky.user.vo.LoginVO;
import com.lucky.user.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final PermissionService permissionService;
    private final UserInfoMapper userInfoMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /**
     * 注册
     * @param registerDTO
     */
    @Override
    // 注册跨 4 张表，加事务保证要么全成功、要么全回滚
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        // 1. 先查手机号是否已存在
        if (userMapper.existsByPhone(registerDTO.getPhone())) {
            throw new BusinessException("该手机号已注册");
        }
        // 2. 密码以 Bcrypt 加密存储；昵称默认使用用户id（先取雪花id再落库）
        Long id = IdUtil.getSnowflakeNextId();
        User user = new User();
        user.setId(id);
        user.setPhone(registerDTO.getPhone());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickName(String.valueOf(id));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该手机号已注册");
        }
        // 3. 用户扩展信息：粉丝数/关注数初始为 0
        UserInfo userInfo = new UserInfo();
        userInfo.setId(IdUtil.getSnowflakeNextId());
        userInfo.setUserId(id);
        userInfo.setFans(0);
        userInfo.setFollowee(0);
        userInfoMapper.insert(userInfo);
        // 4. 手机号记录
        UserPhone userPhone = new UserPhone();
        userPhone.setId(IdUtil.getSnowflakeNextId());
        userPhone.setUserId(id);
        userPhone.setPhone(registerDTO.getPhone());
        userPhoneMapper.insert(userPhone);
        // 5. 默认绑定普通吧友角色：按 role_code 查，避免角色 id 调整后误绑高权限角色
        Role defaultRole = roleMapper.selectByRoleCode(RoleType.NORMAL_USER.getCode());
        if (defaultRole == null) {
            throw new BusinessException("默认角色未配置");
        }
        UserRole userRole = new UserRole();
        userRole.setId(IdUtil.getSnowflakeNextId());
        userRole.setUserId(id);
        userRole.setRoleId(defaultRole.getId());
        userRoleMapper.insert(userRole);
    }

    /**
     * 登录
     * @param loginDTO
     * @return
     */
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 0. 防刷：失败次数超限直接拒绝
        if (loginAttemptService.isBlocked(loginDTO.getPhone())) {
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }
        // 1. 按手机号查用户
        User user = userMapper.selectByPhone(loginDTO.getPhone());
        // 账号不存在与密码错误统一提示，避免暴露用户是否存在
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(loginDTO.getPhone());
            throw new BusinessException("账号或密码错误");
        }
        loginAttemptService.clear(loginDTO.getPhone());
        // 2. 签发双 Token 并登记会话状态(短 token 返回前端，长 token 由 Controller 写 Cookie)
        var issued = tokenIssuer.issuePair(user.getId());
        TokenDTO tokens = new TokenDTO(issued.accessToken(), issued.refreshToken());
        // 3. 查用户全部角色，角色身份随登录一并返回(前端据此展示/路由)
        List<Role> roles = permissionService.listRoles(user.getId());
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


    /**
     * 自助编辑资料：改的是 access token 里的登录人
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UpdateProfileDTO updateProfileDTO) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("未登录，请先登录");
        }
        doUpdateProfile(userId, updateProfileDTO);
    }

    /**
     * 指定目标编辑资料：改的是 userId 指向的用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfileForUser(Long userId, UpdateProfileDTO updateProfileDTO) {
        doUpdateProfile(userId, updateProfileDTO);
    }

    /**
     * 真正的写库逻辑，供上面两个入口共用
     */
    private void doUpdateProfile(Long userId, UpdateProfileDTO updateProfileDTO) {
        if (updateProfileDTO.getNickName() != null || updateProfileDTO.getIcon() != null) {
            userMapper.updateProfile(userId, updateProfileDTO.getNickName(), updateProfileDTO.getIcon());
        }
        if (updateProfileDTO.getCity() != null || updateProfileDTO.getIntroduce() != null
                || updateProfileDTO.getGender() != null || updateProfileDTO.getBirthday() != null) {
            userInfoMapper.updateProfile(userId, updateProfileDTO.getCity(), updateProfileDTO.getIntroduce(),
                    updateProfileDTO.getGender(), updateProfileDTO.getBirthday());
        }
    }
}
