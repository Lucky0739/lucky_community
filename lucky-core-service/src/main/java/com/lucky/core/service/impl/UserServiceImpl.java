package com.lucky.core.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lucky.core.dto.LoginDTO;
import com.lucky.core.dto.RegisterDTO;
import com.lucky.core.dto.TokenDTO;
import com.lucky.core.entity.User;
import com.lucky.core.entity.UserInfo;
import com.lucky.core.entity.UserPhone;
import com.lucky.core.entity.UserRole;
import com.lucky.core.mapper.UserInfoMapper;
import com.lucky.core.mapper.UserMapper;
import com.lucky.core.mapper.UserPhoneMapper;
import com.lucky.core.mapper.UserRoleMapper;
import com.lucky.core.service.TokenRedisService;
import com.lucky.core.service.UserService;
import com.lucky.core.utils.JwtUtil;
import com.lucky.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 默认角色：普通用户(tb_role.id) */
    private static final Long DEFAULT_ROLE_ID = 1L;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private TokenRedisService tokenRedisService;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private UserPhoneMapper userPhoneMapper;

    @Resource
    private UserRoleMapper userRoleMapper;

    @Override
    // 注册跨 4 张表，加事务保证要么全成功、要么全回滚
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        // 1. 先查手机号是否已存在(拦掉绝大多数重复提交)
        boolean exists = lambdaQuery().eq(User::getPhone, registerDTO.getPhone()).exists();
        if (exists) {
            throw new BusinessException("该手机号已注册");
        }
        // 2. 密码以 BCrypt 加密存储；昵称默认使用用户id（先取雪花id再落库）
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

        // 5. 默认绑定普通用户角色(tb_role.role_code = USER, id = 1)
        UserRole userRole = new UserRole();
        userRole.setId(IdWorker.getId());
        userRole.setUserId(id);
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insert(userRole);
    }

    @Override
    public TokenDTO login(LoginDTO loginDTO) {
        // 1. 按手机号查用户
        User user = lambdaQuery().eq(User::getPhone, loginDTO.getPhone()).one();
        // 账号不存在与密码错误统一提示，避免暴露用户是否存在
        // 密码以 Bcrypt 存储，用 matches 比对
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }

        // 2. 生成双 Token：短 token 返回前端(内存)，长 token 由 Controller 写 Cookie
        TokenDTO tokenDTO = jwtUtil.createTokenPair(user.getId());

        // 3. 长 token 的会话存 Redis（jti -> userId），用于续期/轮换/踢下线
        String refreshJti = jwtUtil.parse(tokenDTO.getRefreshToken(), JwtUtil.TYPE_REFRESH).jti();
        tokenRedisService.saveRefreshSession(user.getId(), refreshJti, jwtUtil.getRefreshExpire());
        return tokenDTO;
    }

    @Override
    public TokenDTO refreshAccessToken(String refreshToken) {
        // 1. 校验 refresh token：签名/类型/时效任一不过即视为未登录
        JwtUtil.Payload payload = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (payload == null) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        // 2. Redis 会话校验：不存在 = 已登出/被踢/该 refresh 已被轮换过
        if (!tokenRedisService.isRefreshSessionValid(payload.userId(), payload.jti())) {
            // 复用检测：已轮换掉的旧 jti 再次出现 => 疑似 token 被盗 => 清空该用户全部会话
            tokenRedisService.kickOut(payload.userId(), null, 0);
            throw new BusinessException("登录状态异常，请重新登录");
        }
        // 3. 轮换：删旧 refresh 会话，签发新的一对 token 并重新登记会话
        tokenRedisService.removeRefreshSession(payload.userId(), payload.jti());
        TokenDTO tokenDTO = jwtUtil.createTokenPair(payload.userId());
        String newRefreshJti = jwtUtil.parse(tokenDTO.getRefreshToken(), JwtUtil.TYPE_REFRESH).jti();
        tokenRedisService.saveRefreshSession(payload.userId(), newRefreshJti, jwtUtil.getRefreshExpire());
        return tokenDTO;
    }
    @Override
    public void logout(String accessToken, String refreshToken) {
        // 1. 优先用 access：能解析出 jti 就一并拉黑(剩余有效期内立即失效)
        JwtUtil.Payload access = jwtUtil.parse(accessToken, JwtUtil.TYPE_ACCESS);
        if (access != null) {
            tokenRedisService.kickOut(access.userId(), access.jti(), jwtUtil.getAccessExpire());
            return;
        }
        // 2. access 失效(过期/残缺/被篡改)：用 refresh 兜底解析 userId，照样清空该用户全部会话
        JwtUtil.Payload refresh = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (refresh != null) {
            tokenRedisService.kickOut(refresh.userId(), null, 0);
        }
        // 3. 两者都无 → 无可清理，调用方清除 Cookie 即可
    }

    @Override
    public void kickOut(Long userId) {
        // 踢下线(不改密码/封号等场景)：清空该用户全部 refresh 会话；access 待其自然过期或由黑名单拦
        tokenRedisService.kickOut(userId, null, 0);
    }
}
