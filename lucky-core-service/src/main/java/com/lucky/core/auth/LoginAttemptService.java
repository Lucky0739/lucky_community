package com.lucky.core.auth;

import com.lucky.redis.RedisKeyManage;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录防刷：Redis 固定窗口计数，失败次数达到上限即锁定该手机号
 * <p>按手机号(账号)维度计数，防撞库/暴力破解；固定窗口从第一次失败起算，窗口内失败累计、
 * 成功即清零、窗口过期自动解锁。
 */
@Service
public class LoginAttemptService {

    /** 统计窗口内允许的最大失败次数 */
    @Value("${login.max-fail-count:5}")
    private int maxFailCount;

    /** 统计窗口时长(秒) */
    @Value("${login.fail-window-seconds:900}")
    private long failWindowSeconds;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 该手机号当前是否已被锁定(失败次数达到上限) */
    public boolean isBlocked(String phone) {
        String value = stringRedisTemplate.opsForValue().get(RedisKeyManage.LOGIN_FAIL_COUNT.key(phone));
        if (value == null) {
            return false;
        }
        return Integer.parseInt(value) >= maxFailCount;
    }

    /** 登录失败：计数 +1；首次失败时设置窗口 TTL(固定窗口，不随后续失败刷新) */
    public void recordFailure(String phone) {
        String key = RedisKeyManage.LOGIN_FAIL_COUNT.key(phone);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, failWindowSeconds, TimeUnit.SECONDS);
        }
    }

    /** 登录成功：清空失败计数 */
    public void clear(String phone) {
        stringRedisTemplate.delete(RedisKeyManage.LOGIN_FAIL_COUNT.key(phone));
    }
}
