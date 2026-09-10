package com.lucky.core.controller;

import com.lucky.common.Result;
import com.lucky.constant.Constant;
import com.lucky.core.dto.LoginDTO;
import com.lucky.core.dto.RegisterDTO;
import com.lucky.core.dto.TokenDTO;
import com.lucky.core.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {


    @Value("${jwt.refresh-expire:604800}")
    private long refreshExpireSeconds;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Resource
    private UserService userService;

    /**
     * 注册：手机号 + 密码，密码服务端以 BCrypt 加密后落库
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Valid RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success();
    }

    /**
     * 登录
     * 短 token(access)：放入响应体，前端存内存，请求头携带
     * 长 token(refresh)：写入 HttpOnly Cookie，用于过期后静默续期
     */
    @PostMapping
    public Result<String> login(@RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        TokenDTO tokenDTO = userService.login(loginDTO);
        // refresh token 仅服务端可读，浏览器不会随每次请求自动暴露给 JS
        Cookie cookie = new Cookie(Constant.REFRESH_TOKEN_COOKIE, tokenDTO.getRefreshToken());
        cookie.setHttpOnly(true);                    // JS 不可读，防 XSS 窃取
        cookie.setSecure(cookieSecure);              // 仅 HTTPS 发送(生产开 true)
        cookie.setAttribute("SameSite", "Lax");      // 阻止跨站请求携带，缓解 CSRF
        cookie.setPath("/user/refresh");             // 收窄作用域，仅在续期接口生效
        cookie.setMaxAge((int) refreshExpireSeconds);// 与 refresh token 有效期一致
        response.addCookie(cookie);
        return Result.success(tokenDTO.getAccessToken());
    }
    /**
     * 刷新：用 HttpOnly Cookie 里的 refresh token 换发新的 access token
     */
    @GetMapping("/refresh")
    public Result<String> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
        String accessToken = userService.refreshAccessToken(refreshToken);
        return Result.success(accessToken);
    }
}
