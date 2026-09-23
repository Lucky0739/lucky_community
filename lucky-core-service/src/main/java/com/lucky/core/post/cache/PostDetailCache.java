package com.lucky.core.post.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.lucky.redis.RedisKeyManage;
import com.lucky.core.post.vo.PostDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 帖子详情的多级缓存：Caffeine(L1，本机内存) + Redis(L2，实例间共享)
 *
 * <h3>读路径</h3>
 * <pre>
 * L1 命中且未逻辑过期 → 返回
 * L1 命中但逻辑过期   → 返回旧值 + 异步重建（不阻塞）
 * L1 未命中          → 读 L2，命中则回填 L1
 * L2 也未命中        → 冷路径 → DCL → 查库 → 回填 L2 + L1
 * </pre>
 *
 * <h3>两种击穿，两种手段</h3>
 * <table>
 *   <tr><td>冷 key（没有旧值可用）</td><td>DCL 互斥，读最多等 200ms</td></tr>
 *   <tr><td>热 key 逻辑过期（有旧值）</td><td>返回旧值 + 异步重建，读不阻塞</td></tr>
 * </table>
 * <b>有旧值就异步重建，没旧值才加锁等</b>——两者不冲突，各管一段。
 * <h3>为什么要保留 Caffeine 的 {@code get(key, loader)}</h3>
 * 它在 L1 命中时<b>不调用</b> loader、未命中才调用，所以一个调用同时覆盖了
 * 「冷路径要走 loader」和「逻辑过期直接返回缓存」两种语义。
 * 更关键的是它提供了<b>本机单飞</b>：同一个 JVM 内只有一个线程会走到 DCL，
 * 把分布式锁的并发压力从「每线程一次」压到「每实例一次」。
 *
 * <h3>已接受的代价</h3>
 * <ul>
 *   <li>多实例下 L1 最长会陈旧一个 {@code logical-ttl-seconds}（这是逻辑过期换「读不阻塞」的代价）</li>
 *   <li>被软删的帖子 ID 仍在布隆过滤器里，每次读要打一条 selectById 才发现是空的</li>
 *   <li>缓存条目反序列化失败（格式变更、VO 加字段）会退化为一次回源，<b>刻意不宽容处理</b>：
 *       宁可回源也不要因为字段缺失而返回错误数据</li>
 * </ul>
 */
@Slf4j
@Service
public class PostDetailCache {

    private final Cache<Long, PostDetailCacheEntry> localCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper cacheObjectMapper;
    private final RedissonClient redissonClient;
    private final ThreadPoolTaskExecutor cacheRebuildExecutor;
    private final PostCacheInvalidator invalidator;

    /** 逻辑过期：超过它就返回旧值并触发异步重建 */
    private final long logicalTtlSeconds;
    /** 两级的硬 TTL 兜底。逻辑过期意味着 key 永不真过期，没有它 Redis 内存会一直涨 */
    private final long hardTtlSeconds;
    /** 逻辑过期时间的抖动百分比，打散同一批写入的 key */
    private final int jitterPercent;
    /** 冷路径等锁上限：超过就降级直接查库 */
    private final long loadLockWaitMillis;
    /** 加载锁租期，防止持锁实例崩溃后死锁 */
    private final long loadLockLeaseMillis;
    /** 异步重建的锁租期 */
    private final long rebuildLeaseSeconds;

    /** 本机去重：正在异步重建的 postId。挡住同一个实例的重复触发 */
    private final Set<Long> rebuilding = ConcurrentHashMap.newKeySet();

    public PostDetailCache(@Qualifier("postDetailLocalCache") Cache<Long, PostDetailCacheEntry> localCache,
                           StringRedisTemplate stringRedisTemplate,
                           @Qualifier("cacheObjectMapper") ObjectMapper cacheObjectMapper,
                           RedissonClient redissonClient,
                           @Qualifier("cacheRebuildExecutor") ThreadPoolTaskExecutor cacheRebuildExecutor,
                           PostCacheInvalidator invalidator,
                           @Value("${post.cache.logical-ttl-seconds:60}") long logicalTtlSeconds,
                           @Value("${post.cache.hard-ttl-seconds:200}") long hardTtlSeconds,
                           @Value("${post.cache.ttl-jitter-percent:10}") int jitterPercent,
                           @Value("${post.cache.load-lock-wait-millis:200}") long loadLockWaitMillis,
                           @Value("${post.cache.load-lock-lease-millis:3000}") long loadLockLeaseMillis,
                           @Value("${post.cache.rebuild-lease-seconds:5}") long rebuildLeaseSeconds) {
        this.localCache = localCache;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheObjectMapper = cacheObjectMapper;
        this.redissonClient = redissonClient;
        this.cacheRebuildExecutor = cacheRebuildExecutor;
        this.invalidator = invalidator;
        this.logicalTtlSeconds = logicalTtlSeconds;
        this.hardTtlSeconds = hardTtlSeconds;
        this.jitterPercent = jitterPercent;
        this.loadLockWaitMillis = loadLockWaitMillis;
        this.loadLockLeaseMillis = loadLockLeaseMillis;
        this.rebuildLeaseSeconds = rebuildLeaseSeconds;
    }
    /**
     * 读：Caffeine → Redis → 查数据库（双重检测锁：Caffeine查不到就去Redis，Redis查不到就去获取锁，
     * 拿到锁后再次查看redis是否有数据，没有的话最后去查询数据库）
     */
    public PostDetailVO get(Long postId, Function<Long, PostDetailVO> loader) {
        PostDetailCacheEntry postDetailCacheEntry = localCache.get(postId, id -> loadEntry(id, loader));
        if (postDetailCacheEntry == null) {
            return null;
        }
        if (postDetailCacheEntry.getData() == null) {
            // 真出现时绕过缓存直接回源——返回 null 会让真实帖子 404，那比多查一次库严重得多
            log.error("缓存条目缺少数据，绕过缓存直接回源，postId={}", postId);
            return loader.apply(postId);
        }
        if (postDetailCacheEntry.isLogicallyExpired()) {
            // 有旧值可用 → 不阻塞，先返回旧值，后台重建
            triggerAsyncRebuild(postId, loader);
        }
        return postDetailCacheEntry.getData();
    }

    /**
     * 写入 L1 + L2，供热点预热使用
     */
    public void put(PostDetailVO vo) {
        if (vo == null || vo.getPostId() == null) {
            return;
        }
        PostDetailCacheEntry entry = newEntry(vo);
        localCache.put(vo.getPostId(), entry);
        putToL2(entry);
    }
    /**
     * 失效 L1 + L2
     */
    public void evict(Long postId) {
        if (postId == null) {
            return;
        }
        localCache.invalidate(postId);
        try {
            stringRedisTemplate.delete(RedisKeyManage.POST_DETAIL.key(postId));
        } catch (Exception e) {
            log.warn("删除 L2 缓存失败，postId={}", postId, e);
        }
        invalidator.publishEvict(postId);
    }
    // ==================== 冷路径 ====================
    private PostDetailCacheEntry loadEntry(Long postId, Function<Long, PostDetailVO> loader) {
        PostDetailCacheEntry postDetailCacheEntry = getFromL2(postId);
        if (postDetailCacheEntry != null) {
            return postDetailCacheEntry;
        }
        return loadWithLock(postId, loader);
    }
    /**
     * 双重检测锁：跨实例保证同一个帖子只有一个加载者
     */
    private PostDetailCacheEntry loadWithLock(Long postId, Function<Long, PostDetailVO> loader) {
        RLock lock = redissonClient.getLock(RedisKeyManage.POST_LOAD_LOCK.key(postId));
        boolean locked = false;
        try {
            locked = lock.tryLock(loadLockWaitMillis, loadLockLeaseMillis, TimeUnit.MILLISECONDS);
            if (locked) {
                // 二次检测：等锁期间别的实例可能已经建好了
                PostDetailCacheEntry again = getFromL2(postId);
                if (again != null) {
                    return again;
                }
                return loadAndStore(postId, loader);
            }
            // 抢不到锁说明持锁者耗时超过了等待上限。不能继续等——锁持有者一旦卡住
            log.debug("等待加载锁超时，降级直接回源，postId={}", postId);
            return loadAndStore(postId, loader);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loadAndStore(postId, loader);
        } catch (Exception e) {
            // 锁服务（Redis）不可用不能把读接口带挂
            log.warn("分布式加载锁异常，降级直接回源，postId={}", postId, e);
            return loadAndStore(postId, loader);
        } finally {
            unlockQuietly(lock, locked);
        }
    }
    /**
     * 触发异步重建。两层去重：本机标记 + 跨实例非阻塞锁
     */
    private void triggerAsyncRebuild(Long postId, Function<Long, PostDetailVO> loader) {
        if (!rebuilding.add(postId)) {
            return;
        }
        try {
            cacheRebuildExecutor.execute(() -> {
                try {
                    rebuild(postId, loader);
                } finally {
                    rebuilding.remove(postId);
                }
            });
        } catch (RejectedExecutionException e) {
            // 必须清掉标记：否则这个 key 会因为一次拒绝而永久停止重建
            rebuilding.remove(postId);
            log.warn("重建线程池已满，放弃本次重建（继续服务旧值，下次读会重新触发），postId={}", postId);
        }
    }

    private void rebuild(Long postId, Function<Long, PostDetailVO> loader) {
        RLock lock = redissonClient.getLock(RedisKeyManage.POST_LOAD_LOCK.key(postId));
        boolean locked = false;
        try {
            // 绝不等待：抢不到说明别的实例正在重建，直接放弃
            locked = lock.tryLock(0, rebuildLeaseSeconds, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            // 二次检测：排队期间可能已被别的实例刷新过了
            PostDetailCacheEntry latest = getFromL2(postId);
            if (latest != null && !latest.isLogicallyExpired()) {
                localCache.put(postId, latest);   // 顺手把本机 L1 对齐
                return;
            }
            PostDetailCacheEntry fresh = loadAndStore(postId, loader);
            if (fresh != null) {
                localCache.put(postId, fresh);
                log.debug("帖子缓存异步重建完成，postId={}", postId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("异步重建帖子缓存失败，将继续服务旧值，postId={}", postId, e);
        } finally {
            unlockQuietly(lock, locked);
        }
    }

    // ==================== 存储层读写 ====================
    private PostDetailCacheEntry loadAndStore(Long postId, Function<Long, PostDetailVO> loader) {
        PostDetailVO vo = loader.apply(postId);
        if (vo == null) {
            return null;
        }
        PostDetailCacheEntry entry = newEntry(vo);
        putToL2(entry);
        return entry;
    }

    private PostDetailCacheEntry newEntry(PostDetailVO vo) {
        PostDetailCacheEntry entry = new PostDetailCacheEntry();
        entry.setData(vo);
        entry.setLogicalExpireAt(LocalDateTime.now().plusSeconds(logicalTtlSeconds + jitterSeconds()));
        return entry;
    }

    /**
     * 抖动只加不减：同一批写入的 key 若逻辑过期时间完全相同，会在同一秒集体触发重建。
     * 只加不减是为了不缩短预期的数据新鲜度。
     */
    private long jitterSeconds() {
        if (jitterPercent <= 0) {
            return 0L;
        }
        long range = logicalTtlSeconds * jitterPercent / 100;
        return range <= 0 ? 0L : ThreadLocalRandom.current().nextLong(range + 1);
    }

    // Caffeine查询数据查不到就去查询Redis
    private PostDetailCacheEntry getFromL2(Long postId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyManage.POST_DETAIL.key(postId));
            if (json == null) {
                return null;
            }
            // 反序列化并返回
            return cacheObjectMapper.readValue(json, PostDetailCacheEntry.class);
        } catch (Exception e) {
            // 缓存挂了或者条目格式不兼容（老格式、VO 改过字段）都要降级回源，不能把接口带挂
            log.warn("读取帖子缓存失败，降级回源，postId={}", postId, e);
            return null;
        }
    }

    private void putToL2(PostDetailCacheEntry entry) {
        try {
            // 硬 TTL 只是兜底：新鲜度由 entry.logicalExpireAt 决定，这个 TTL 只为防止 Redis 内存无限涨
            stringRedisTemplate.opsForValue().set(
                    RedisKeyManage.POST_DETAIL.key(entry.getData().getPostId()),
                    cacheObjectMapper.writeValueAsString(entry),
                    hardTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入帖子缓存失败，postId={}", entry.getData().getPostId(), e);
        }
    }

    /**
     * 释放锁；租期已过或锁已被别人持有时跳过，任何异常都只记日志
     */
    private void unlockQuietly(RLock lock, boolean locked) {
        if (!locked) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("释放帖子缓存锁失败，将由租期自动过期，lock={}", lock.getName(), e);
        }
    }
}
