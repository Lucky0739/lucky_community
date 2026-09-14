package com.lucky.core.service;

import com.lucky.core.utils.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 会话签发的唯一入口：把「签 token」和「登记会话状态」焊在一起
 * <p>
 * 为什么要收口：access 白名单对「签发了但没登记」的 token 是 fail-closed 的——
 * 漏登记 = 用户拿到一个立刻 401 的 token。而签发点散落在登录和刷新两处时，
 * 漏登记恰恰是这类 bug 的经典成因。这里让 {@link JwtUtil} 只负责签，
 * 本类负责「签了就必须登记」，两个调用点都只走本类。
 */
@Service
public class TokenIssuer {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private TokenRedisService tokenRedisService;

    /**
     * 签发一对双 Token 并完成登记：refresh 建会话（jti -> userId），access jti 进白名单
     * @return 新的一对 token 及各自 jti
     */
    public JwtUtil.IssuedTokens issuePair(Long userId) {
        JwtUtil.IssuedTokens issued = jwtUtil.createTokenPair(userId);
        // 先登记 refresh 会话，调用方随后才写 Cookie：宁可多一个无人持有的会话，也不让用户拿到无会话的 token
        tokenRedisService.saveRefreshSession(userId, issued.refreshJti(), jwtUtil.getRefreshExpire());
        tokenRedisService.whitelistAccess(userId, issued.accessJti(), jwtUtil.getAccessExpire());
        return issued;
    }

    /**
     * 只签发一个 access 并登记白名单，不发放新的 refresh 凭证
     * <p>用于刷新宽限路径：把并发/重复刷新能换到的东西限制在短期 access 内，
     * 攻击者即便在宽限窗口内重放，也拿不到可用于链式续期的 refresh token。
     *
     * @return 新 access token
     */
    public String issueAccessOnly(Long userId) {
        JwtUtil.IssuedAccess access = jwtUtil.createAccessToken(userId);
        tokenRedisService.whitelistAccess(userId, access.jti(), jwtUtil.getAccessExpire());
        return access.token();
    }
}
