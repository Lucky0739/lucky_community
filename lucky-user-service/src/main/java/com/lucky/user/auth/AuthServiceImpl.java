package com.lucky.user.auth;

import com.lucky.common.auth.JwtUtil;
import com.lucky.common.auth.TokenRedisService;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.exception.UnauthorizedException;
import com.lucky.user.auth.dto.RefreshResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 并发轮换时，落败方等待抢占者写入新 jti 的最长时间(毫秒) */
    private static final long ROTATION_WAIT_MILLIS = 500;

    /** 等待轮换结果的轮询间隔(毫秒) */
    private static final long ROTATION_POLL_MILLIS = 20;

    private final JwtUtil jwtUtil;
    private final TokenIssuer tokenIssuer;
    private final TokenRedisService tokenRedisService;

    @Override
    public RefreshResult refreshAccessToken(String refreshToken) {
        JwtUtil.Payload payload = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (payload == null) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        if (tokenRedisService.isRefreshSessionValid(payload.userId(), payload.jti())) {
            long grace = jwtUtil.getRefreshGraceSeconds();
            if (tokenRedisService.tryClaimRotation(payload.jti(), grace)) {
                JwtUtil.IssuedTokens issued = tokenIssuer.issuePair(payload.userId());
                tokenRedisService.removeRefreshSession(payload.userId(), payload.jti());
                tokenRedisService.completeRotation(payload.jti(), issued.refreshJti(), grace);
                return new RefreshResult(issued.accessToken(), issued.refreshToken());
            }
            RefreshResult reused = reuseRotatedSession(payload.userId(), payload.jti());
            if (reused != null) {
                return reused;
            }
            throw new BusinessException("刷新繁忙，请稍后重试");
        }

        RefreshResult graceReuse = reuseRotatedSession(payload.userId(), payload.jti());
        if (graceReuse != null) {
            log.warn("refresh 在宽限窗口内被重复使用，userId={}, jti={}", payload.userId(), payload.jti());
            return graceReuse;
        }

        log.warn("refresh 复用疑似盗用，userId={}, jti={}", payload.userId(), payload.jti());
        throw new UnauthorizedException("登录状态异常，请重新登录");
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        JwtUtil.Payload access = jwtUtil.parse(accessToken, JwtUtil.TYPE_ACCESS);
        if (access != null && tokenRedisService.isAccessWhitelisted(access.userId(), access.jti())) {
            tokenRedisService.kickOut(access.userId());
            return;
        }

        JwtUtil.Payload refresh = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (refresh != null && tokenRedisService.isRefreshSessionValid(refresh.userId(), refresh.jti())) {
            tokenRedisService.kickOut(refresh.userId());
        }
    }

    @Override
    public void kickOut(Long userId) {
        tokenRedisService.kickOut(userId);
    }

    @Override
    public void revoke(Long userId) {
        tokenRedisService.revokeUser(userId, jwtUtil.getRefreshExpire());
    }

    private RefreshResult reuseRotatedSession(Long userId, String oldJti) {
        String newJti = awaitRotation(oldJti);
        if (newJti == null || !tokenRedisService.isRefreshSessionValid(userId, newJti)) {
            return null;
        }
        return new RefreshResult(tokenIssuer.issueAccessOnly(userId), null);
    }

    private String awaitRotation(String oldJti) {
        long deadline = System.currentTimeMillis() + ROTATION_WAIT_MILLIS;
        while (true) {
            String value = tokenRedisService.getRotation(oldJti);
            if (!TokenRedisService.ROTATION_PENDING.equals(value)) {
                return value;
            }
            if (System.currentTimeMillis() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(ROTATION_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
