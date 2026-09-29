package com.lucky.user.auth;

import com.lucky.user.auth.dto.RefreshResult;

/**
 * 会话与凭证：刷新 / 登出 / 踢下线 / 撤销
 */
public interface AuthService {

    /**
     * 用 refresh token 换发新的 access token（轮换 + 复用检测 + 并发宽限）
     */
    RefreshResult refreshAccessToken(String refreshToken);

    /**
     * 登出：清空该用户全部会话
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 踢下线：清空指定用户的全部会话
     */
    void kickOut(Long userId);

    /**
     * 撤销该用户：清空会话 <b>并且</b> 拒绝其后续所有已签发 token，
     */
    void revoke(Long userId);
}
