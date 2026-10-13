package com.lucky.user.controller;

import com.lucky.common.Result;
import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.constant.Constant;
import com.lucky.common.file.FileService;
import com.lucky.user.auth.AuthService;
import com.lucky.user.auth.cookie.RefreshCookieUtil;
import com.lucky.user.auth.dto.RefreshResult;
import com.lucky.user.dto.LoginDTO;
import com.lucky.user.dto.RegisterDTO;
import com.lucky.user.dto.UpdateProfileDTO;
import com.lucky.user.service.UserService;
import com.lucky.user.api.vo.UserBriefVO;
import com.lucky.user.vo.LoginVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    @Value("${jwt.refresh-expire:604800}")
    private long refreshExpireSeconds;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    private final UserService userService;
    private final AuthService authService;
    private final FileService fileService;

    /**
     * 注册：手机号 + 密码，密码服务端以 Bcrypt 加密后落库
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
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        LoginVO loginVO = userService.login(loginDTO);
        RefreshCookieUtil.write(response, loginVO.getRefreshToken(), refreshExpireSeconds, cookieSecure);
        return Result.success(loginVO);
    }

    /**
     * 刷新：用 HttpOnly Cookie 里的 refresh token 换发新的 access token
     */
    @PostMapping("/refresh")
    public Result<String> refresh(@CookieValue(value = Constant.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                                  HttpServletResponse response) {
        RefreshResult result = authService.refreshAccessToken(refreshToken);
        if (result.rotated()) {
            RefreshCookieUtil.write(response, result.newRefreshToken(), refreshExpireSeconds, cookieSecure);
        }
        return Result.success(result.accessToken());
    }
    /**
     * 登出：清空该用户全部会话（access 与 refresh 一并即时失效）并清除 Cookie
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @CookieValue(value = Constant.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                                 HttpServletResponse response) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        // access 为主，失效时用 refresh 兜底，保证任何情况下都能断掉服务端凭证
        authService.logout(accessToken, refreshToken);
        RefreshCookieUtil.clear(response, cookieSecure);
        return Result.success();
    }

    /**
     * 作者昵称和头像。帖子详情调用，不要求登录。
     */
    @GetMapping("/brief/{userId}")
    public Result<UserBriefVO> brief(@PathVariable("userId") Long userId) {
        return Result.success(userService.getBrief(userId));
    }

    /**
     * 上传头像：文件存 MinIO，返回可访问 URL
     */
    @AuthRequired
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, FileService.DIR_AVATAR));
    }

    /**
     * 编辑个人资料
     */
    @AuthRequired
    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody @Valid UpdateProfileDTO updateProfileDTO) {
        userService.updateProfile(updateProfileDTO);
        return Result.success();
    }




}
