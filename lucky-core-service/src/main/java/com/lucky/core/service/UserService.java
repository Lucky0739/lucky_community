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
     * 用 refresh token 换发新的 access token（短 token 依据长 token 续期）
     *
     * @param refreshToken 请求头/接口传入的长 token
     * @return 新签发的 access token；refresh 无效或过期抛业务异常
     */
    String refreshAccessToken(String refreshToken);
}
