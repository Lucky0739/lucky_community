package com.lucky.community.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.common.bloom.LongBloomFilter;
import com.lucky.redis.RedisKeyManage;
import com.lucky.community.vo.BarVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 贴吧详情的缓存：{@code bar:detail:{barId}} -> {@link BarVO} 的 JSON，或不存在的占位 {@code null}
 *
 * <h3>读路径（缓存 → 布隆 → DB）</h3>
 * 布隆只在 Redis miss 时决定要不要回源，不当存在性门卫。热路径一次 GET；
 * 布隆假阴性时已缓存的吧仍能返回。
 *
 * <h3>双层防穿透</h3>
 * <ol>
 *   <li>布隆过滤器：一定不存在的 ID 直接返回，不打 DB。
 *       随机 ID 空间无穷，把它们都 SET 成空值会把 Redis 打满。</li>
 *   <li>Redis 空值：布隆允许假阳性，{@code mightContain=true} 但库里没有时，
 *       把 JSON {@code null} 写入短 TTL。</li>
 * </ol>
 * 布隆 {@code false} 默认短路。按比例抽样回源：库里有则补写并累计假阴性，达到阈值后整体 fail-open。
 */
@Slf4j
@Service
public class BarDetailCache {

    /** JSON {@code null}。key 存在且值为这个，表示「确认没有这个吧」，与 key 不存在（未缓存）区分开 */
    static final String NULL_PLACEHOLDER = "null";

    private static final String LOAD_LOCK_PREFIX = "bar:load:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper cacheObjectMapper;
    private final LongBloomFilter barBloomFilter;
    private final RedissonClient redissonClient;

    private final long ttlSeconds;
    private final long nullTtlSeconds;
    private final int jitterPercent;
    private final int falseNegativeSamplePercent;
    private final long loadLockWaitMillis;
    private final long loadLockLeaseMillis;

    public BarDetailCache(StringRedisTemplate stringRedisTemplate,
                          @Qualifier("cacheObjectMapper") ObjectMapper cacheObjectMapper,
                          LongBloomFilter barBloomFilter,
                          RedissonClient redissonClient,
                          @Value("${bar.cache.ttl-seconds:1800}") long ttlSeconds,
                          @Value("${bar.cache.null-ttl-seconds:60}") long nullTtlSeconds,
                          @Value("${bar.cache.ttl-jitter-percent:10}") int jitterPercent,
                          @Value("${bar.bloom.false-negative-sample-percent:1}") int falseNegativeSamplePercent,
                          @Value("${bar.bloom.load-lock-wait-millis:200}") long loadLockWaitMillis,
                          @Value("${bar.bloom.load-lock-lease-millis:3000}") long loadLockLeaseMillis) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheObjectMapper = cacheObjectMapper;
        this.barBloomFilter = barBloomFilter;
        this.redissonClient = redissonClient;
        this.ttlSeconds = ttlSeconds;
        this.nullTtlSeconds = nullTtlSeconds;
        this.jitterPercent = jitterPercent;
        this.falseNegativeSamplePercent = falseNegativeSamplePercent;
        this.loadLockWaitMillis = loadLockWaitMillis;
        this.loadLockLeaseMillis = loadLockLeaseMillis;
    }

    /**
     * 读：Redis → 布隆 → 回源。VO 与空值都回填 Redis。同一 barId 回源单飞。
     */
    public BarVO get(Long barId, Function<Long, BarVO> loader) {
        if (barId == null) {
            return null;
        }
        CachedBar cached = getFromCache(barId);
        if (cached.hit) {
            return cached.vo;
        }
        boolean maybeExists = barBloomFilter.mightContain(barId);
        if (!maybeExists && !shouldSampleFalseNegative()) {
            return null;
        }
        return loadWithLock(barId, loader, !maybeExists);
    }

    /**
     * 写入缓存。供预热或已知数据变更后主动刷新使用
     */
    public void put(BarVO vo) {
        if (vo == null || vo.getBarId() == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyManage.BAR_DETAIL.key(vo.getBarId()),
                    cacheObjectMapper.writeValueAsString(vo),
                    ttlWithJitter(ttlSeconds), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入贴吧详情缓存失败，barId={}", vo.getBarId(), e);
        }
    }

    /**
     * 失效缓存：吧信息被修改时调用。空值占位一并删掉。
     */
    public void evict(Long barId) {
        if (barId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(RedisKeyManage.BAR_DETAIL.key(barId));
        } catch (Exception e) {
            log.warn("删除贴吧详情缓存失败，barId={}", barId, e);
        }
    }

    private BarVO loadWithLock(Long barId, Function<Long, BarVO> loader, boolean sampledBloomMiss) {
        RLock lock = redissonClient.getLock(LOAD_LOCK_PREFIX + barId);
        boolean locked = false;
        try {
            locked = lock.tryLock(loadLockWaitMillis, loadLockLeaseMillis, TimeUnit.MILLISECONDS);
            if (locked) {
                CachedBar again = getFromCache(barId);
                if (again.hit) {
                    return again.vo;
                }
                return loadAndStore(barId, loader, sampledBloomMiss);
            }
            CachedBar raced = getFromCache(barId);
            if (raced.hit) {
                return raced.vo;
            }
            return loadAndStore(barId, loader, sampledBloomMiss);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loadAndStore(barId, loader, sampledBloomMiss);
        } catch (Exception e) {
            log.warn("贴吧加载锁异常，降级回源，barId={}", barId, e);
            return loadAndStore(barId, loader, sampledBloomMiss);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("释放贴吧加载锁失败，barId={}", barId, e);
                }
            }
        }
    }

    private BarVO loadAndStore(Long barId, Function<Long, BarVO> loader, boolean sampledBloomMiss) {
        BarVO fresh = loader.apply(barId);
        if (fresh != null) {
            if (sampledBloomMiss) {
                barBloomFilter.reportFalseNegative();
            }
            put(fresh);
            return fresh;
        }
        putNull(barId);
        return null;
    }

    private boolean shouldSampleFalseNegative() {
        if (falseNegativeSamplePercent <= 0) {
            return false;
        }
        if (falseNegativeSamplePercent >= 100) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) < falseNegativeSamplePercent;
    }

    private void putNull(Long barId) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyManage.BAR_DETAIL.key(barId),
                    NULL_PLACEHOLDER,
                    ttlWithJitter(nullTtlSeconds), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入贴吧空值缓存失败，barId={}", barId, e);
        }
    }

    /**
     * key 不存在 → miss；值为 JSON {@code null} → 命中空值；否则反序列化为 VO。
     */
    private CachedBar getFromCache(Long barId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyManage.BAR_DETAIL.key(barId));
            if (json == null) {
                return CachedBar.miss();
            }
            if (NULL_PLACEHOLDER.equals(json)) {
                return CachedBar.absent();
            }
            return CachedBar.hit(cacheObjectMapper.readValue(json, BarVO.class));
        } catch (Exception e) {
            // 缓存挂了、或者条目格式不兼容（VO 改过字段）都降级回源，不能把读接口带挂
            log.warn("读取贴吧详情缓存失败，降级回源，barId={}", barId, e);
            return CachedBar.miss();
        }
    }

    private long ttlWithJitter(long baseSeconds) {
        if (jitterPercent <= 0 || baseSeconds <= 0) {
            return Math.max(baseSeconds, 1L);
        }
        long range = baseSeconds * jitterPercent / 100;
        long jitter = range <= 0 ? 0L : ThreadLocalRandom.current().nextLong(range + 1);
        return baseSeconds + jitter;
    }

    private static final class CachedBar {
        final boolean hit;
        final BarVO vo;

        private CachedBar(boolean hit, BarVO vo) {
            this.hit = hit;
            this.vo = vo;
        }

        static CachedBar miss() {
            return new CachedBar(false, null);
        }

        static CachedBar absent() {
            return new CachedBar(true, null);
        }

        static CachedBar hit(BarVO vo) {
            if (vo == null) {
                return absent();
            }
            return new CachedBar(true, vo);
        }
    }
}
