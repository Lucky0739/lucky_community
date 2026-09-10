package com.lucky.core.controller;

import com.lucky.common.Result;
import com.lucky.constant.Constant;
import com.lucky.core.annotation.AuthRequired;
import com.lucky.core.cookie.RefreshCookieUtil;
import com.lucky.core.dto.LoginDTO;
import com.lucky.core.dto.RegisterDTO;
import com.lucky.core.dto.TokenDTO;
import com.lucky.core.service.UserService;
import com.lucky.core.utils.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
        RefreshCookieUtil.write(response, tokenDTO.getRefreshToken(), refreshExpireSeconds, cookieSecure);
        return Result.success(tokenDTO.getAccessToken());
    }

    /**
     * 刷新：用 HttpOnly Cookie 里的 refresh token 换发新的一对 access token
     */
    @GetMapping("/refresh")
    public Result<String> refresh(@CookieValue(value = Constant.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                                  HttpServletResponse response) {
        TokenDTO tokenDTO = userService.refreshAccessToken(refreshToken);
        // 轮换后的新 refresh 写回 Cookie，前端拿到新 access
        RefreshCookieUtil.write(response, tokenDTO.getRefreshToken(), refreshExpireSeconds, cookieSecure);
        return Result.success(tokenDTO.getAccessToken());
    }
    /**
     * 登出：拉黑当前 access + 清空 refresh 会话，并清除 Cookie
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @CookieValue(value = Constant.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                                 HttpServletResponse response) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        // access 为主，失效时用 refresh 兜底，保证任何情况下都能断掉服务端凭证
        userService.logout(accessToken, refreshToken);
        RefreshCookieUtil.clear(response, cookieSecure);
        return Result.success();
    }

    /**
     * 受保护接口示例：必须携带合法且未被拉黑的 access token，否则 401
     */
    @AuthRequired
    @GetMapping("/me")
    public Result<Long> me() {
        return Result.success(UserContext.getUserId());
    }
}
