package com.lucky.core.service;

import com.lucky.constant.Constant;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Token 的 Redis 存储：refresh 会话 + access 黑名单（踢下线/登出）
 */
@Service
public class TokenRedisService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 登录/续期后：登记 refresh 会话（jti -> userId），并纳入该用户会话集合 */
    public void saveRefreshSession(Long userId, String refreshJti, long refreshExpireSeconds) {
        stringRedisTemplate.opsForValue().set(Constant.REFRESH_SESSION_PREFIX + refreshJti,
                String.valueOf(userId), refreshExpireSeconds, TimeUnit.SECONDS);
        String userKey = Constant.REFRESH_USER_PREFIX + userId;
        stringRedisTemplate.opsForSet().add(userKey, refreshJti);
        stringRedisTemplate.expire(userKey, refreshExpireSeconds, TimeUnit.SECONDS);
    }

    /** refresh 会话是否存在且属于该用户 */
    public boolean isRefreshSessionValid(Long userId, String refreshJti) {
        String v = stringRedisTemplate.opsForValue().get(Constant.REFRESH_SESSION_PREFIX + refreshJti);
        return v != null && v.equals(String.valueOf(userId));
    }

    /** 删除单个 refresh 会话（轮换时删旧；登出时删当前） */
    public void removeRefreshSession(Long userId, String refreshJti) {
        stringRedisTemplate.delete(Constant.REFRESH_SESSION_PREFIX + refreshJti);
        stringRedisTemplate.opsForSet().remove(Constant.REFRESH_USER_PREFIX + userId, refreshJti);
    }

    /** 把 access token 的 jti 加入黑名单，TTL = 其剩余有效期（踢下线/登出立即失效） */
    public void blacklistAccess(String accessJti, long ttlSeconds) {
        if (accessJti != null && ttlSeconds > 0) {
            stringRedisTemplate.opsForValue().set(Constant.BLACKLIST_ACCESS_PREFIX + accessJti, "1",
                    ttlSeconds, TimeUnit.SECONDS);
        }
    }

    /** access 是否已被拉黑 */
    public boolean isAccessBlacklisted(String accessJti) {
        return accessJti != null
                && stringRedisTemplate.hasKey(Constant.BLACKLIST_ACCESS_PREFIX + accessJti);
    }

    /**
     * 踢下线 / 登出：
     * 1) 若给了 access jti，则把它拉黑（剩余有效期内立即失效）
     * 2) 清空该用户全部 refresh 会话，断掉"用 refresh 换新 access"的退路
     */
    public void kickOut(Long userId, String accessJti, long accessTtlSeconds) {
        blacklistAccess(accessJti, accessTtlSeconds);
        String userKey = Constant.REFRESH_USER_PREFIX + userId;
        Set<String> jtis = stringRedisTemplate.opsForSet().members(userKey);
        System.out.println(jtis);
        if (jtis != null) {
            jtis.forEach(jti -> stringRedisTemplate.delete(Constant.REFRESH_SESSION_PREFIX + jti));
        }
        stringRedisTemplate.delete(userKey);
    }
}

