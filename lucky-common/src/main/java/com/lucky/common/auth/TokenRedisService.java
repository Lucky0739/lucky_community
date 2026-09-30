package com.lucky.common.auth;

import com.lucky.redis.RedisKeyManage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Token 的 Redis 存储：refresh 会话 + access 白名单（踢下线）+ 轮换宽限记录
 */
public class TokenRedisService {

    /**
     * 轮换抢占的占位值：表示「某个请求正在轮换这个 jti，结果还没写好」
     */
    public static final String ROTATION_PENDING = "PENDING";

    private final StringRedisTemplate stringRedisTemplate;

    public TokenRedisService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 登记一个已签发的 access jti（加入白名单）
     */
    public void whitelistAccess(Long userId, String accessJti, long accessExpireSeconds) {
        if (accessJti == null) {
            return;
        }
        String key = RedisKeyManage.ACCESS_USER.key(userId);
        stringRedisTemplate.opsForSet().add(key, accessJti);
        stringRedisTemplate.expire(key, accessExpireSeconds, TimeUnit.SECONDS);
    }
    /** 该 access 是否仍在该用户的白名单里（不在 = 未签发/已过期清理/已被踢下线） */
    public boolean isAccessWhitelisted(Long userId, String accessJti) {
        return accessJti != null && Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                        .isMember(RedisKeyManage.ACCESS_USER.key(userId), accessJti));
    }


    /** 登录/续期后：登记 refresh 会话（jti -> userId），并纳入该用户会话集合 */
    public void saveRefreshSession(Long userId, String refreshJti, long refreshExpireSeconds) {
        stringRedisTemplate.opsForValue().set(RedisKeyManage.REFRESH_SESSION.key(refreshJti),
                String.valueOf(userId), refreshExpireSeconds, TimeUnit.SECONDS);
        String userKey = RedisKeyManage.REFRESH_USER.key(userId);
        stringRedisTemplate.opsForSet().add(userKey, refreshJti);
        stringRedisTemplate.expire(userKey, refreshExpireSeconds, TimeUnit.SECONDS);
    }

    /** refresh 会话是否存在且属于该用户 */
    public boolean isRefreshSessionValid(Long userId, String refreshJti) {
        String v = stringRedisTemplate.opsForValue().get(RedisKeyManage.REFRESH_SESSION.key(refreshJti));
        return v != null && v.equals(String.valueOf(userId));
    }

    /** 删除单个 refresh 会话（轮换时删旧；登出时删当前） */
    public void removeRefreshSession(Long userId, String refreshJti) {
        stringRedisTemplate.delete(RedisKeyManage.REFRESH_SESSION.key(refreshJti));
        stringRedisTemplate.opsForSet().remove(RedisKeyManage.REFRESH_USER.key(userId), refreshJti);
    }

    /**
     * 原子抢占旧 jti 的轮换权（SET NX）
     */
    public boolean tryClaimRotation(String oldJti, long graceSeconds) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyManage.ROTATED.key(oldJti), ROTATION_PENDING,
                graceSeconds, TimeUnit.SECONDS));
    }

    /** 轮换完成：把占位值改写成新会话的 jti，供并发方复用 */
    public void completeRotation(String oldJti, String newRefreshJti, long graceSeconds) {
        stringRedisTemplate.opsForValue().set(RedisKeyManage.ROTATED.key(oldJti),
                newRefreshJti, graceSeconds, TimeUnit.SECONDS);
    }

    /**
     * 读轮换记录，原样返回（可能是 {@link #ROTATION_PENDING} 哨兵，也可能是新 jti）
     */
    public String getRotation(String oldJti) {
        return stringRedisTemplate.opsForValue().get(RedisKeyManage.ROTATED.key(oldJti));
    }

    /**
     * 踢下线 / 登出
     */
    public void kickOut(Long userId) {
        stringRedisTemplate.delete(RedisKeyManage.ACCESS_USER.key(userId));

        String userKey = RedisKeyManage.REFRESH_USER.key(userId);
        Set<String> jtis = stringRedisTemplate.opsForSet().members(userKey);
        if (jtis != null) {
            jtis.forEach(jti -> stringRedisTemplate.delete(RedisKeyManage.REFRESH_SESSION.key(jti)));
        }
        stringRedisTemplate.delete(userKey);
    }

    // 撤销

    /**
     * 撤销该用户：写墓碑 + 清空现有会话。用于用户被删除/封禁等「不再允许持有会话」的场景
     */
    public void revokeUser(Long userId, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(RedisKeyManage.REVOKED_USER.key(userId), "1",
                ttlSeconds, TimeUnit.SECONDS);
        kickOut(userId);
    }

    /** 该用户是否已被撤销 */
    public boolean isUserRevoked(Long userId) {
        return stringRedisTemplate.hasKey(RedisKeyManage.REVOKED_USER.key(userId));
    }
}
