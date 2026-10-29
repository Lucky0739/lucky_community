package com.lucky.community.bloom;

import com.lucky.bloom.LongBloomFilter;
import com.lucky.redis.RedisKeyManage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 帖子布隆过滤器定期重建：回收已删除帖子占用的位，把误判率拉回配置值。
 *
 * <p>重建流程：取预热锁 → 设置 fail-open 标记 → {@code clear()} → {@code initAndWarmUp()} → 清除标记。
 * 预热只灌 {@code tb_post} 里 {@code deleted = 0} 的 ID，旧帖子占的位就随位图一起被删掉了。
 *
 * <h3>为什么需要 fail-open 标记</h3>
 * 预热的本质是「先删位图，再一条条 add」。只要位图里已经有一条，{@code mightContain} 就会
 * 把尚未加进去的真实 ID 判成「不存在」——这是假阴性，会让真帖子 404。所以重建期间所有实例
 * 都必须停止相信「过滤器说不存在」，读路径改为直接查库。
 *
 * <p>标记不设 TTL：重建进程崩溃时它必须保留下来持续 fail-open，不能过一会儿自动消失，
 * 否则残缺的位图会重新被信任。人工确认后删除标记即可恢复加速；下次重启也会重新预热。
 * 标记存在期间只是多查库，不影响正确性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostBloomFilterRebuildJob {

    /** 与布隆过滤器同名前缀，便于在 Redis 里一起排查：post:bloom:rebuild:marker */
    private static final String MARKER_SUFFIX = ":rebuild:marker";
    /** 多实例互斥，保证同一时刻只有一个实例在重建 */
    private static final String LOCK_SUFFIX = ":rebuild:lock";
    /**
     * 框架 {@code LongBloomFilter} 的预热锁后缀（见其类注释）。
     * 重建前必须先拿到它：否则 clear() 可能删掉另一个实例灌到一半的位图，
     */
    private static final String WARM_UP_LOCK_SUFFIX = ":warmUp:lock";
    private static final long WARM_UP_LOCK_WAIT_SECONDS = 30L;
    private static final long WARM_UP_LOCK_LEASE_SECONDS = 60L;

    private final LongBloomFilter postBloomFilter;
    private final RedissonClient redissonClient;

    @Qualifier("postBloomRebuildExecutor")
    private final ThreadPoolTaskExecutor postBloomRebuildExecutor;

    /**
     * 定时触发。真正的重建丢到独立线程池，避免长时间占用 Spring 默认的单线程调度器，
     * 把 AI 重试、索引 outbox 这些同样依赖 {@code @Scheduled} 的任务堵住。
     * 设置 {@code post.bloom.rebuild.cron=-} 可关闭。
     */
    @Scheduled(cron = "${post.bloom.rebuild.cron:0 0 4 * * *}")
    public void scheduleRebuild() {
        try {
            postBloomRebuildExecutor.execute(this::rebuild);
        } catch (RejectedExecutionException e) {
            log.warn("上一次布隆过滤器重建仍在进行或已排队，跳过本次触发");
        }
    }

    /**
     * 启动时清理上次重建中断留下的标记。
     *
     * <p>本 bean 依赖过滤器 bean，所以执行到这里时 {@code initAndWarmUp} 已经成功，
     * 位图一定是完整的——即使上次重建崩在半路，本次启动也已经重新灌好了，清标记是安全的。
     * 先拿重建锁：拿不到说明别的实例正在重建，标记必须保留。
     */
    @PostConstruct
    public void clearStaleMarker() {
        RLock lock = redissonClient.getLock(lockKey());
        boolean locked = false;
        try {
            locked = lock.tryLock();
            if (!locked) {
                return;
            }
            if (isRebuilding()) {
                clearMarker();
                log.warn("检测到上次重建中断遗留的标记，已清除，布隆过滤器恢复正常使用");
            }
        } catch (Exception e) {
            log.warn("启动时检查布隆过滤器重建标记失败，保持现状", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 读路径用：重建期间「过滤器说不存在」不可信，必须继续查库。
     *
     * <p>调用方要在 {@code mightContain} 返回 false <b>之后</b>再调用本方法。
     * 顺序不能反：先看标记再看过滤器，可能在「标记未设置」和「开始预热」之间被调度插入，
     * 从而错过保护；反过来则所有危险交错都能被标记兜住。
     *
     * <p>Redis 异常时按「正在重建」处理——多查一次库，不能把真实帖子误判成不存在。
     */
    public boolean isRebuilding() {
        try {
            return redissonClient.getBucket(markerKey(), StringCodec.INSTANCE).isExists();
        } catch (Exception e) {
            log.warn("读取布隆过滤器重建标记失败，按重建中处理（放行到数据库）", e);
            return true;
        }
    }

    private void rebuild() {
        RLock lock = redissonClient.getLock(lockKey());
        boolean locked = false;
        try {
            // 不等待：抢不到说明别的实例已经在重建
            locked = lock.tryLock();
            if (!locked) {
                log.info("其他实例正在重建帖子布隆过滤器，跳过");
                return;
            }

            // 拿框架的预热锁，确认没有实例正在启动预热（预热期间它不对外服务，但位图在写）
            RLock warmUpLock = redissonClient.getLock(warmUpLockKey());
            if (!warmUpLock.tryLock(WARM_UP_LOCK_WAIT_SECONDS, WARM_UP_LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                log.warn("等待布隆过滤器预热锁超时（有实例正在预热），跳过本次重建");
                return;
            }
            try {
                // 先立标记，再动过滤器。顺序反过来会留下一段所有实例都以为过滤器可信的窗口
                setMarker();
                log.info("开始重建帖子布隆过滤器，重建期间读路径降级为直接查库");
                postBloomFilter.clear();
            } finally {
                if (warmUpLock.isHeldByCurrentThread()) {
                    warmUpLock.unlock();
                }
            }

            // 位图已清空。此后即使和别的实例并发预热，也只是重复 add 同一批 ID，幂等且无害
            long startedAt = System.currentTimeMillis();
            postBloomFilter.initAndWarmUp();
            clearMarker();
            log.info("帖子布隆过滤器重建完成，耗时={}ms", System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            // 标记必须保留：残缺的位图不能被重新信任。恢复靠下一次定时重建，或任意实例重启时
            // 的重新预热 + clearStaleMarker。保留标记期间只是多查库，不影响正确性
            log.error("帖子布隆过滤器重建失败，保留 fail-open 标记，读路径持续直接查库", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void setMarker() {
        redissonClient.getBucket(markerKey(), StringCodec.INSTANCE).set("1");
    }

    private void clearMarker() {
        redissonClient.getBucket(markerKey(), StringCodec.INSTANCE).delete();
    }

    private String markerKey() {
        return RedisKeyManage.POST_BLOOM.key() + MARKER_SUFFIX;
    }

    private String lockKey() {
        return RedisKeyManage.POST_BLOOM.key() + LOCK_SUFFIX;
    }

    private String warmUpLockKey() {
        return RedisKeyManage.POST_BLOOM.key() + WARM_UP_LOCK_SUFFIX;
    }
}
