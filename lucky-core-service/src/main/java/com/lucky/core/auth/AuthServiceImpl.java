package com.lucky.core.auth;

import com.lucky.common.exception.BusinessException;
import com.lucky.common.exception.UnauthorizedException;
import com.lucky.core.auth.dto.RefreshResult;
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
        // 1. 校验 refresh token：签名/类型/时效任一不过即视为未登录
        JwtUtil.Payload payload = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (payload == null) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        // 2. 当前会话 → 抢占轮换权。并发请求都会先通过上面/这里的校验，
        //    没有抢占就会各自轮换出两个会话
        if (tokenRedisService.isRefreshSessionValid(payload.userId(), payload.jti())) {
            long grace = jwtUtil.getRefreshGraceSeconds();
            if (tokenRedisService.tryClaimRotation(payload.jti(), grace)) {
                // 2a. 抢到了：先登记新会话、再删旧会话(顺序不可反，否则中间存在「零有效会话」窗口)
                JwtUtil.IssuedTokens issued = tokenIssuer.issuePair(payload.userId());
                tokenRedisService.removeRefreshSession(payload.userId(), payload.jti());
                // 把结果写给并发方，让它们能复用同一个新会话
                tokenRedisService.completeRotation(payload.jti(), issued.refreshJti(), grace);
                return new RefreshResult(issued.tokens().getAccessToken(), issued.tokens().getRefreshToken());
            }
            // 2b. 没抢到：已有请求在轮换，等它写好结果后复用同一会话
            RefreshResult reused = reuseRotatedSession(payload.userId(), payload.jti());
            if (reused != null) {
                return reused;
            }
            throw new BusinessException("刷新繁忙，请稍后重试");
        }

        // 3. 非当前会话：可能是刚被轮换(宽限窗口内)，也可能是重放
        RefreshResult graceReuse = reuseRotatedSession(payload.userId(), payload.jti());
        if (graceReuse != null) {
            // 良性重复：新会话还活着，说明不是盗用，只补一个 access
            // (只发 access 不发 refresh：即便真是重放，攻击者也拿不到可链式续期的凭证)
            log.warn("refresh 在宽限窗口内被重复使用，userId={}, jti={}", payload.userId(), payload.jti());
            return graceReuse;
        }

        // 4. 到此无法区分「盗用 / 客户端崩溃 / 响应丢了 / Redis 抖动」。
        //    不做破坏性 kickOut：那会把用户所有设备一起登出，丢响应(移动网络常态)时就是自伤式 DoS。
        //    只拒绝这一次请求，并留下日志供排查/风控。
        log.warn("refresh 复用疑似盗用，userId={}, jti={}", payload.userId(), payload.jti());
        throw new UnauthorizedException("登录状态异常，请重新登录");
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        // 定位用户时必须同时校验「会话仍存活」，不能只凭 token 里的 userId 就 kickOut：
        // 否则任何持有合法密钥的人都能伪造一个 token 强制登出任意用户(定向 DoS)。
        // 其余路径(AuthInterceptor / refreshAccessToken)都查 Redis，这里必须保持一致。
        //
        // access 为主、refresh 兜底，是为了让「access 恰好过期」的正常登出依然能断掉凭证。
        JwtUtil.Payload access = jwtUtil.parse(accessToken, JwtUtil.TYPE_ACCESS);
        if (access != null && tokenRedisService.isAccessWhitelisted(access.userId(), access.jti())) {
            tokenRedisService.kickOut(access.userId());
            return;
        }

        JwtUtil.Payload refresh = jwtUtil.parse(refreshToken, JwtUtil.TYPE_REFRESH);
        if (refresh != null && tokenRedisService.isRefreshSessionValid(refresh.userId(), refresh.jti())) {
            tokenRedisService.kickOut(refresh.userId());
        }
        // 两者都不对应存活会话 → 无可清理(可能已登出/已过期)，调用方清 Cookie 即可
    }

    @Override
    public void kickOut(Long userId) {
        // 踢下线(登出/改密等场景)：access 白名单 + refresh 会话一并清空，两者都即时生效
        tokenRedisService.kickOut(userId);
    }

    @Override
    public void revoke(Long userId) {
        // 墓碑 TTL 取 refresh 有效期：这段时间过后，指向该 userId 的 access 和 refresh
        // 都已自然过期，墓碑就没有存在的必要了
        tokenRedisService.revokeUser(userId, jwtUtil.getRefreshExpire());
    }

    /**
     * 复用抢占者刚轮换出的会话：等到新 jti 并确认该会话仍存活，存活则只补发一个 access
     * <p>步骤 2b(并发落败方)与步骤 3(宽限窗口内重复使用)判定条件完全相同，只是后续处置不同
     * ——前者超时算「刷新繁忙」，后者算「疑似盗用」，故抽出本方法、由调用方各自决定。
     *
     * @return 可复用的刷新结果(仅含 access)；无可用会话时返回 null，由调用方决定怎么拒绝
     */
    private RefreshResult reuseRotatedSession(Long userId, String oldJti) {
        String newJti = awaitRotation(oldJti);
        if (newJti == null || !tokenRedisService.isRefreshSessionValid(userId, newJti)) {
            return null;
        }
        return new RefreshResult(tokenIssuer.issueAccessOnly(userId), null);
    }

    /**
     * 读取某个旧 jti 的轮换结果；若抢占者还在轮换中(哨兵值)则短暂轮询等待
     * <p>记录不存在时立即返回 null 不等待——那是「确实没有轮换过」，不是等待中的状态
     *
     * @return 新会话的 jti；无记录或等待超时返回 null
     */
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
