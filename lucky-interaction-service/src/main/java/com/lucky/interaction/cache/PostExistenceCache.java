package com.lucky.interaction.cache;

import com.lucky.redis.RedisKeyManage;
import com.lucky.interaction.mapper.PostReadMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 帖子是否存在：本机 Caffeine → Redis 帖子详情 → tb_post。
 * 点赞只需要知道在不在，不把整篇详情放进本服务的本地缓存。
 */
@Slf4j
@Service
public class PostExistenceCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostReadMapper postReadMapper;
    private final Cache<Long, Boolean> localCache;

    public PostExistenceCache(StringRedisTemplate stringRedisTemplate, PostReadMapper postReadMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.postReadMapper = postReadMapper;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .build();
    }

    public boolean exists(Long postId) {
        Boolean local = localCache.getIfPresent(postId);
        if (local != null) {
            return local;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyManage.POST_DETAIL.key(postId));
            if (json != null) {
                localCache.put(postId, Boolean.TRUE);
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("读取帖子 Redis 缓存失败，改查数据库，postId={}", postId, e);
        }
        boolean exists = postReadMapper.existsActive(postId);
        localCache.put(postId, exists);
        return exists;
    }
}
