package com.lucky.core.controller;

import com.lucky.common.Result;
import com.lucky.constant.Constant;
import com.lucky.core.annotation.AuthRequired;
import com.lucky.core.cookie.RefreshCookieUtil;
import com.lucky.core.dto.user.AppointBarAdminDTO;
import com.lucky.core.dto.user.LoginDTO;
import com.lucky.core.dto.RefreshResult;
import com.lucky.core.dto.user.RegisterDTO;
import com.lucky.core.dto.user.UpdateProfileDTO;
import com.lucky.core.service.FileService;
import com.lucky.core.service.UserService;
import com.lucky.core.utils.UserContext;
import com.lucky.core.vo.LoginVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Resource
    private FileService fileService;

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
    @PostMapping
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        LoginVO loginVO = userService.login(loginDTO);
        RefreshCookieUtil.write(response, loginVO.getRefreshToken(), refreshExpireSeconds, cookieSecure);
        return Result.success(loginVO);
    }

    /**
     * 刷新：用 HttpOnly Cookie 里的 refresh token 换发新的 access token
     * 正常轮换会同时下发新的 refresh 并写回 Cookie；并发/重复刷新走宽限路径，
     * 只补一个 access 且<b>不</b>改写 Cookie（避免覆盖别处刚轮换出的新会话）
     */
    @PostMapping("/refresh")
    public Result<String> refresh(@CookieValue(value = Constant.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                                  HttpServletResponse response) {
        RefreshResult result = userService.refreshAccessToken(refreshToken);
        if (result.rotated()) {
            RefreshCookieUtil.write(response, result.newRefreshToken(), refreshExpireSeconds, cookieSecure);
        }
        return Result.success(result.accessToken());
    }
    /**
     * 登出：清空该用户全部会话（access 与 refresh 一并即时失效）并清除 Cookie
     * 注意是全局语义：其他设备也会被一起登出
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

    /**
     * 平台管理员任命吧务：写入任命记录并给目标用户绑定 BAR_ADMIN 角色
     */
    @AuthRequired
    @PostMapping("/bar-admin/appoint")
    public Result<String> appointBarAdmin(@RequestBody @Valid AppointBarAdminDTO dto) {
        userService.appointBarAdmin(UserContext.getUserId(), dto);
        return Result.success();
    }

    /**
     * 上传头像：文件存 MinIO，返回可访问 URL
     */
    @AuthRequired
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file, "avatar"));
    }

    /**
     * 编辑个人资料：昵称/头像/城市/介绍/性别/生日，字段为空表示不修改
     */
    @AuthRequired
    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody @Valid UpdateProfileDTO dto) {
        userService.updateProfile(UserContext.getUserId(), dto);
        return Result.success();
    }


}
