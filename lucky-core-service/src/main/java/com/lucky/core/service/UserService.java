package com.lucky.core.service;

import com.lucky.core.dto.LoginDTO;
import com.lucky.core.dto.RegisterDTO;
import com.lucky.core.dto.TokenDTO;

public interface UserService {

    /**
     * 注册：手机号查重后落库，密码以 BCrypt 加密存储
     */
    void register(RegisterDTO registerDTO);

    /**
     * 登录：校验手机号+密码，成功返回双 Token（短 access 返回前端，长 refresh 写 Cookie）
     */
    TokenDTO login(LoginDTO loginDTO);

    /**
     * 用 refresh token 换发新的一对 token（轮换 + 复用检测）
     *
     * @param refreshToken 长 token（来自 HttpOnly Cookie）
     * @return 新的 access + refresh 对
     */
    TokenDTO refreshAccessToken(String refreshToken);

    /**
     * 登出：优先用 access 解析用户并拉黑其 jti；access 失效时用 refresh 兜底解析用户，
     * 保证任何情况下都清空该用户的 refresh 会话
     *
     * @param accessToken  短 token（可能为空/已过期）
     * @param refreshToken 长 token（来自 Cookie，可能为空）
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 踢下线：清空指定用户的全部会话
     */
    void kickOut(Long userId);
}
