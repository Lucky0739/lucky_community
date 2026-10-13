package com.lucky.common.bloom;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 按 Long 主键判断「可能存在」。说没有就可以不查库；说有，或者过滤器自己不可信，就必须查库。
 * 容量和误判率在第一次创建后固定。位图用 SETBIT，不需要 RedisBloom 模块。
 */
@Slf4j
public class LongBloomFilter {

    private static final String WARMED_SUFFIX = ":warmed";
    private static final String LOCK_SUFFIX = ":warmUp:lock";
    private static final long LOCK_WAIT_SECONDS = 30L;
    private static final long LOCK_LEASE_SECONDS = 60L;

    private final RedissonClient redissonClient;
    private final String name;
    private final long expectedInsertions;
    private final double falseProbability;
    private final Supplier<Collection<Long>> allIdsLoader;
    /** 假阴性累计到这个数就不再信任过滤器。小于等于 0 只记日志 */
    private final int falseNegativeDegradeThreshold;
    private final AtomicInteger falseNegatives = new AtomicInteger();

    private RBloomFilter<Long> filter;
    /** 为 false 时，查询一律当作「可能存在」，交给数据库判断 */
    private volatile boolean usable;

    /**
     * @param allIdsLoader 启动时加载已有主键，必须是 Long
     */
    public LongBloomFilter(RedissonClient redissonClient,
                           String name,
                           long expectedInsertions,
                           double falseProbability,
                           Supplier<Collection<Long>> allIdsLoader,
                           int falseNegativeDegradeThreshold) {
        this.redissonClient = redissonClient;
        this.name = name;
        this.expectedInsertions = expectedInsertions;
        this.falseProbability = falseProbability;
        this.allIdsLoader = allIdsLoader;
        this.falseNegativeDegradeThreshold = falseNegativeDegradeThreshold;
    }

    /** 创建过滤器。已有数据没灌完时会重新灌，避免漏掉旧 ID。 */
    public void initAndWarmUp() {
        try {
            RBloomFilter<Long> bloom = redissonClient.getBloomFilter(name);
            boolean created = bloom.tryInit(expectedInsertions, falseProbability);
            this.filter = bloom;
            this.usable = true;
            if (!created && warmedMarkerExists() && bloom.count() > 0) {
                log.info("布隆过滤器已就绪，跳过预热，name={}，已有元素约 {} 个", name, bloom.count());
                return;
            }
            warmUp(created);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("布隆过滤器预热被中断：" + name, e);
        } catch (Exception e) {
            throw new IllegalStateException("布隆过滤器初始化失败：" + name, e);
        }
    }

    private void warmUp(boolean created) throws InterruptedException {
        RLock lock = redissonClient.getLock(name + LOCK_SUFFIX);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("等待布隆过滤器预热锁超时，可能有其他实例正在预热：" + name);
            }
            if (!created && warmedMarkerExists() && filter.count() > 0) {
                log.info("等待期间其他实例已完成预热，跳过，name={}", name);
                return;
            }
            long startedAt = System.currentTimeMillis();
            Collection<Long> ids = allIdsLoader.get();
            ids.forEach(filter::add);
            // 标记最后写。中途失败时没有标记，下次启动会再灌一遍
            redissonClient.<String>getBucket(warmedMarker(), StringCodec.INSTANCE).set("1");
            log.info("布隆过滤器预热完成，name={}，元素数={}，耗时={}ms",
                    name, ids.size(), System.currentTimeMillis() - startedAt);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 新建一条记录后写入。写失败就不再信任过滤器。 */
    public void add(long id) {
        if (!usable || filter == null) {
            return;
        }
        try {
            filter.add(id);
        } catch (Exception e) {
            this.usable = false;
            log.error("布隆过滤器写入失败，之后查询改为直接查库，name={}，id={}", name, id, e);
        }
    }

    /** 库里确认有这条记录、过滤器里却没有时补进去。成功后恢复信任。 */
    public void backfill(long id) {
        if (filter == null) {
            return;
        }
        try {
            filter.add(id);
            if (!usable) {
                usable = true;
                log.info("布隆过滤器补写成功，恢复可信，name={}，id={}", name, id);
            }
        } catch (Exception e) {
            usable = false;
            log.warn("布隆过滤器补写失败，查询继续直接查库，name={}，id={}", name, id, e);
        }
    }

    /**
     * @return false 表示一定没有，可以不查库；true 表示可能有，或者过滤器不可信，都要查库
     */
    public boolean mightContain(long id) {
        if (!usable || filter == null) {
            return true;
        }
        try {
            if (filter.contains(id)) {
                return true;
            }
            // 位图被清掉时 count 为 0，不能把所有 ID 都判成不存在
            return filter.count() == 0;
        } catch (Exception e) {
            log.warn("布隆过滤器查询失败，改为查库，name={}，id={}", name, id, e);
            return true;
        }
    }

    /** 过滤器说没有、库里却有。次数够了就改为直接查库。 */
    public void reportFalseNegative() {
        int count = falseNegatives.incrementAndGet();
        log.error("布隆过滤器假阴性，已补写并累计，name={}，count={}", name, count);
        if (falseNegativeDegradeThreshold > 0 && count >= falseNegativeDegradeThreshold) {
            this.usable = false;
            log.error("布隆过滤器假阴性达到阈值，之后查询改为直接查库，name={}，threshold={}",
                    name, falseNegativeDegradeThreshold);
        }
    }

    private String warmedMarker() {
        return name + WARMED_SUFFIX;
    }

    private boolean warmedMarkerExists() {
        return redissonClient.getBucket(warmedMarker(), StringCodec.INSTANCE).isExists();
    }
}
