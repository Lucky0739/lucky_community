package com.lucky.user.auth;

import com.lucky.redis.RedisKeyManage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录防刷：Redis 固定窗口计数，失败次数达到上限即锁定该手机号
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    @Value("${login.max-fail-count:5}")
    private int maxFailCount;

    @Value("${login.fail-wait-seconds:900}")
    private long failWaitSeconds;

    private final StringRedisTemplate stringRedisTemplate;

    public boolean isBlocked(String phone) {
        String value = stringRedisTemplate.opsForValue().get(RedisKeyManage.LOGIN_FAIL_COUNT.key(phone));
        if (value == null) {
            return false;
        }
        return Integer.parseInt(value) >= maxFailCount;
    }

    public void recordFailure(String phone) {
        String key = RedisKeyManage.LOGIN_FAIL_COUNT.key(phone);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, failWaitSeconds, TimeUnit.SECONDS);
        }
    }

    public void clear(String phone) {
        stringRedisTemplate.delete(RedisKeyManage.LOGIN_FAIL_COUNT.key(phone));
    }
}
