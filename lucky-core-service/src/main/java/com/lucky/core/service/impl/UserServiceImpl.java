package com.lucky.core.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lucky.exception.BusinessException;
import com.lucky.core.dto.LoginDTO;
import com.lucky.core.dto.RegisterDTO;
import com.lucky.core.dto.TokenDTO;
import com.lucky.core.entity.User;
import com.lucky.core.mapper.UserMapper;
import com.lucky.core.service.UserService;
import com.lucky.core.utils.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void register(RegisterDTO registerDTO) {
        // 1. 手机号查重
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
        save(user);
    }

    @Override
    public TokenDTO login(LoginDTO loginDTO) {
        // 1. 按手机号查用户
        User user = lambdaQuery().eq(User::getPhone, loginDTO.getPhone()).one();
        // 账号不存在与密码错误统一提示，避免暴露用户是否存在
        // 密码以 BCrypt 存储，用 matches 比对
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
       }
        // 2. 生成双 Token：短 token 返回前端(内存)，长 token 由 Controller 写 Cookie
        TokenDTO tokenDTO = new TokenDTO();
        tokenDTO.setAccessToken(jwtUtil.createAccessToken(user.getId()));
        tokenDTO.setRefreshToken(jwtUtil.createRefreshToken(user.getId()));
        String accessToken=tokenDTO.getAccessToken();
        String refreshToken=tokenDTO.getRefreshToken();
        System.out.println(accessToken);
        return tokenDTO;
    }

    @Override
    public String refreshAccessToken(String refreshToken) {
        // 校验 refresh token：签名/类型/时效任一不过即视为未登录
        Long userId = jwtUtil.parseToken(refreshToken, "refresh");
        if (userId == null) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        return jwtUtil.createAccessToken(userId);
    }
}
