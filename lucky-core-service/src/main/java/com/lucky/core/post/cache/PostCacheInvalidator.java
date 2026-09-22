package com.lucky.core.post.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.lucky.redis.RedisKeyManage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 跨实例的 L1 缓存失效广播
 *
 * <h3>解决什么</h3>
 * L1 是进程内的，A 实例清了缓存，B 实例的 L1 完全不知道，只能等它自己过期。
 * 用 Redis 的发布订阅把失效事件广播给所有实例，各自清掉本机的 L1。
 *
 * <h3>依赖方向</h3>
 * {@code PostDetailCache -> PostCacheInvalidator -> Cache bean}。
 * <b>不能反过来让本类依赖 {@code PostDetailCache}</b>——那会形成循环依赖。
 * 所以本类只持有 Caffeine 的 {@code Cache} bean，不持有那个服务。
 *
 * <h3>当前触发点</h3>
 * AI 审核完成（或失败）后会 {@code PostDetailCache#evict}，让详情里的
 * {@code analysisStatus} / 风险字段及时刷新。改帖 / 删帖同样 evict。
 *
 * <h3>为什么用 Redisson 的 RTopic 而不是 Spring 的 RedisMessageListenerContainer</h3>
 * 前者复用已经接好的 {@code RedissonClient}，三十行就能写完；后者要额外配连接工厂、
 * 监听器容器和线程池，为一个失效通道不值得。
 */
@Slf4j
@Service
public class PostCacheInvalidator {

    private final RedissonClient redissonClient;
    private final Cache<Long, PostDetailCacheEntry> localCache;

    public PostCacheInvalidator(RedissonClient redissonClient,
                                @Qualifier("postDetailLocalCache") Cache<Long, PostDetailCacheEntry> localCache) {
        this.redissonClient = redissonClient;
        this.localCache = localCache;
    }

    /**
     * 订阅失效广播
     * <p>这里失败只记 warn 不抛：失效广播失效的后果是「别的实例的 L1 要多等一个逻辑过期周期」，
     * 是性能问题不是正确性问题。与布隆过滤器启动失败必须中断启动的情况不同——
     * 那条链路错了会让真帖子 404。启动要响、运行要稳，但「响」只针对正确性相关的组件。
     */
    @PostConstruct
    public void subscribe() {
        try {
            topic().addListener(String.class, (channel, postId) -> {
                try {
                    localCache.invalidate(Long.valueOf(postId));
                    log.debug("收到缓存失效广播，已清本机 L1，postId={}", postId);
                } catch (NumberFormatException e) {
                    log.warn("缓存失效广播内容非法，忽略，payload={}", postId);
                }
            });
            log.info("帖子缓存失效广播已订阅，channel={}", RedisKeyManage.POST_CACHE_TOPIC.key());
        } catch (Exception e) {
            log.warn("订阅帖子缓存失效广播失败，跨实例 L1 失效将退化为等 TTL，channel={}",
                    RedisKeyManage.POST_CACHE_TOPIC.key(), e);
        }
    }

    /**
     * 广播失效。当前实例也会收到自己发的消息并重复清一次 L1，无害
     */
    public void publishEvict(Long postId) {
        try {
            topic().publish(String.valueOf(postId));
        } catch (Exception e) {
            log.warn("广播缓存失效失败，其他实例的 L1 将等到过期才刷新，postId={}", postId, e);
        }
    }

    /** 用 StringCodec 收发，值在 redis-cli 里可读，排查方便 */
    private RTopic topic() {
        return redissonClient.getTopic(RedisKeyManage.POST_CACHE_TOPIC.key(), StringCodec.INSTANCE);
    }
}
