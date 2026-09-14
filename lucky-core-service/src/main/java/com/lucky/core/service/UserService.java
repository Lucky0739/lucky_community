package com.lucky.core.service;

import com.lucky.core.dto.user.AppointBarAdminDTO;
import com.lucky.core.dto.user.LoginDTO;
import com.lucky.core.dto.RefreshResult;
import com.lucky.core.dto.user.RegisterDTO;
import com.lucky.core.vo.LoginVO;

public interface UserService {

    /**
     * 注册：手机号查重后落库，密码以 BCrypt 加密存储
     */
    void register(RegisterDTO registerDTO);

    /**
     * 登录：校验手机号+密码，成功返回登录信息（用户身份/角色 + 短 access，长 refresh 由 Controller 写 Cookie）
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 用 refresh token 换发新的 access token（轮换 + 复用检测 + 并发宽限）
     * <p>三种结果：
     * <ul>
     *   <li>正常轮换：新 access + 新 refresh（调用方需写回 Cookie）</li>
     *   <li>宽限复用：只发新 access，无新 refresh（并发/重复刷新，调用方<b>不要</b>动 Cookie）</li>
     *   <li>疑似重放：抛 {@code UnauthorizedException}，<b>不</b>波及其他设备会话</li>
     * </ul>
     *
     * @param refreshToken 长 token（来自 HttpOnly Cookie）
     * @return 新 access；若产生了新会话则一并返回新 refresh
     */
    RefreshResult refreshAccessToken(String refreshToken);

    /**
     * 登出：清空该用户全部会话
     * <p><b>全局语义</b>——access 白名单与 refresh 会话一并清空，其他设备也会被一起登出。
     * access 或 refresh 任一可解析出身份即可定位用户，两者都失效则无可清理。
     *
     * @param accessToken  短 token（可能为空/已过期）
     * @param refreshToken 长 token（来自 Cookie，可能为空）
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 踢下线：清空指定用户的全部会话
     */
    void kickOut(Long userId);

    /**
     * 平台管理员任命吧务：写入任命记录并给目标用户绑定 BAR_ADMIN 角色
     * <p>仅平台管理员可调用；同一用户在同一个吧最多一条吧务身份(唯一索引兜底)
     *
     * @param grantUserId 任命人(当前登录的平台管理员)
     * @param dto         贴吧ID + 被任命用户ID
     */
    void appointBarAdmin(Long grantUserId, AppointBarAdminDTO dto);
}
