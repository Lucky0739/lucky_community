package com.lucky.user.auth;

import com.lucky.common.auth.JwtUtil;
import com.lucky.common.auth.TokenRedisService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 会话签发的唯一入口：把「签 token」和「登记会话状态」焊在一起
 */
@Service
public class TokenIssuer {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private TokenRedisService tokenRedisService;

    /**
     * 签发一对双 Token 并完成登记：refresh 建会话（jti -> userId），access jti 进白名单
     */
    public JwtUtil.IssuedTokens issuePair(Long userId) {
        JwtUtil.IssuedTokens issued = jwtUtil.createTokenPair(userId);
        tokenRedisService.saveRefreshSession(userId, issued.refreshJti(), jwtUtil.getRefreshExpire());
        tokenRedisService.whitelistAccess(userId, issued.accessJti(), jwtUtil.getAccessExpire());
        return issued;
    }

    /**
     * 只签发一个 access 并登记白名单，不发放新的 refresh 凭证
     */
    public String issueAccessOnly(Long userId) {
        JwtUtil.IssuedAccess access = jwtUtil.createAccessToken(userId);
        tokenRedisService.whitelistAccess(userId, access.jti(), jwtUtil.getAccessExpire());
        return access.token();
    }
}
