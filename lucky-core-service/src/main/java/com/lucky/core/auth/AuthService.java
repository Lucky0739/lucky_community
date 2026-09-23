package com.lucky.core.auth;

import com.lucky.core.auth.dto.RefreshResult;

/**
 * 会话与凭证：刷新 / 登出 / 踢下线 / 撤销
 * <p>与 {@code UserService} 拆开——登录仍由用户领域校验身份后调 {@link TokenIssuer}，
 * 但「token 生命周期」不再塞进用户资料服务里。
 */
public interface AuthService {

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
     * <p>语义是「作废当前会话」，对方之后仍可重新登录。用于登出、改密等场景
     */
    void kickOut(Long userId);

    /**
     * 撤销该用户：清空会话 <b>并且</b> 拒绝其后续所有已签发 token，
     * 直到墓碑过期（refresh 有效期）为止
     *
     * <p>凡「用户不再有效」的路径都必须调本方法，只调 {@link #kickOut} 是不够的：
     * 会话键会在并发刷新中被重新写入，用户照样能访问并持续续期
     * （详见 {@code TokenRedisService#revokeUser}）。用于删除用户、封号
     */
    void revoke(Long userId);
}
