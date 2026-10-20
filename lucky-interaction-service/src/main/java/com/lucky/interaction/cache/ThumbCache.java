package com.lucky.interaction.cache;

import com.lucky.interaction.constant.RedisKeyConstants;
import com.lucky.interaction.constant.ThumbConstant;
import com.lucky.interaction.enums.ThumbLuaResult;
import com.lucky.interaction.enums.ThumbUnlikeLuaResult;
import com.lucky.redis.RedisKeyManage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 点赞热路径：RedisBloom 判重，Hash 存点赞数据，计数单独一个 key。都由 Lua 一次写完。
 */
@Service
public class ThumbCache {

    private static final int MADD_BATCH = 500;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> thumbLikeScript;
    private final DefaultRedisScript<Long> thumbBloomInitScript;
    private final DefaultRedisScript<Long> thumbUnlikeScript;
    private final DefaultRedisScript<Long> thumbUnlikeRollbackScript;
    private final DefaultRedisScript<Long> thumbCountStatScript;
    private final DefaultRedisScript<List<String>> thumbCountDrainScript;
    private final DefaultRedisScript<Long> countFlushUnlockScript;

    public ThumbCache(StringRedisTemplate stringRedisTemplate,
                      @Qualifier("thumbLikeScript") DefaultRedisScript<Long> thumbLikeScript,
                      @Qualifier("thumbBloomInitScript") DefaultRedisScript<Long> thumbBloomInitScript,
                      @Qualifier("thumbUnlikeScript") DefaultRedisScript<Long> thumbUnlikeScript,
                      @Qualifier("thumbUnlikeRollbackScript") DefaultRedisScript<Long> thumbUnlikeRollbackScript,
                      @Qualifier("thumbCountStatScript") DefaultRedisScript<Long> thumbCountStatScript,
                      @Qualifier("thumbCountDrainScript") DefaultRedisScript<List<String>> thumbCountDrainScript,
                      @Qualifier("countFlushUnlockScript") DefaultRedisScript<Long> countFlushUnlockScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.thumbLikeScript = thumbLikeScript;
        this.thumbBloomInitScript = thumbBloomInitScript;
        this.thumbUnlikeScript = thumbUnlikeScript;
        this.thumbUnlikeRollbackScript = thumbUnlikeRollbackScript;
        this.thumbCountStatScript = thumbCountStatScript;
        this.thumbCountDrainScript = thumbCountDrainScript;
        this.countFlushUnlockScript = countFlushUnlockScript;
    }

    /**
     * 计数 key 不在时，先用库里的行数灌上再交给 Lua +1，避免过期后从 0 起算。
     */
    public void preloadCountIfAbsent(Long postId, long dbCount) {
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyManage.THUMB_COUNT.key(postId),
                String.valueOf(dbCount),
                Duration.ofSeconds(ThumbConstant.COUNT_TTL_SECONDS));
    }

    // 执行Lua脚本
    public ThumbLuaResult like(Long userId, Long postId) {
        Long code = stringRedisTemplate.execute(
                thumbLikeScript,
                thumbKeys(userId, postId),
                String.valueOf(postId),
                String.valueOf(ThumbConstant.BLOOM_TTL_SECONDS),
                String.valueOf(ThumbConstant.DATA_TTL_SECONDS),
                String.valueOf(ThumbConstant.COUNT_TTL_SECONDS));
        return ThumbLuaResult.fromCode(code);
    }

    /**
     * @param dbConfirmed 布隆命中但 Hash 没有这条时，调用方已经用 tb_thumb 确认过才传 true
     */
    public ThumbUnlikeLuaResult unlike(Long userId, Long postId, boolean dbConfirmed) {
        Long code = stringRedisTemplate.execute(
                thumbUnlikeScript,
                thumbKeys(userId, postId),
                String.valueOf(postId),
                String.valueOf(ThumbConstant.BLOOM_TTL_SECONDS),
                String.valueOf(ThumbConstant.DATA_TTL_SECONDS),
                String.valueOf(ThumbConstant.COUNT_TTL_SECONDS),
                dbConfirmed ? "1" : "0");
        return ThumbUnlikeLuaResult.fromCode(code);
    }

    /**
     * 取消点赞的落库消息发送失败时，把 Hash、计数和取消集合恢复成已点赞。
     */
    public void rollbackUnlike(Long userId, Long postId) {
        stringRedisTemplate.execute(
                thumbUnlikeRollbackScript,
                thumbKeys(userId, postId),
                String.valueOf(postId),
                String.valueOf(ThumbConstant.BLOOM_TTL_SECONDS),
                String.valueOf(ThumbConstant.DATA_TTL_SECONDS),
                String.valueOf(ThumbConstant.COUNT_TTL_SECONDS));
    }

    private List<String> thumbKeys(Long userId, Long postId) {
        return List.of(
                RedisKeyConstants.buildBloomUserPostLikeListKey(userId),
                RedisKeyManage.THUMB_USER.key(userId),
                RedisKeyManage.THUMB_COUNT.key(postId),
                RedisKeyConstants.buildThumbCancelKey(userId));
    }

    public boolean hasCount(Long postId) {
        return stringRedisTemplate.hasKey(RedisKeyManage.THUMB_COUNT.key(postId));
    }

    /**
     * userId:postId为键值，thumbId为值，作一个待落库标志
     */
    public void markUnsynced(Long userId, Long postId, Long thumbId) {
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstants.buildThumbUnsyncedKey(userId, postId),
                String.valueOf(thumbId),
                Duration.ofDays(1));
    }


    public Long unsyncedId(Long userId, Long postId) {
        String value = stringRedisTemplate.opsForValue().get(RedisKeyConstants.buildThumbUnsyncedKey(userId, postId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void clearUnsynced(Long userId, Long postId) {
        stringRedisTemplate.delete(RedisKeyConstants.buildThumbUnsyncedKey(userId, postId));
    }

    /**
     * 布隆 key 不存在时 BF.RESERVE 建空过滤器，再把该用户已经点过的帖子 BF.MADD 进去。
     */
    public void initBloom(Long userId, List<Long> likedPostIds) {
        String key = RedisKeyConstants.buildBloomUserPostLikeListKey(userId);
        String ttl = String.valueOf(ThumbConstant.BLOOM_TTL_SECONDS);
        String errorRate = ThumbConstant.BLOOM_ERROR_RATE;
        String capacity = String.valueOf(ThumbConstant.BLOOM_CAPACITY);
        if (likedPostIds == null || likedPostIds.isEmpty()) {
            stringRedisTemplate.execute(thumbBloomInitScript, List.of(key), ttl, errorRate, capacity);
            return;
        }
        for (int from = 0; from < likedPostIds.size(); from += MADD_BATCH) {
            int to = Math.min(from + MADD_BATCH, likedPostIds.size());
            List<String> args = new ArrayList<>(to - from + 3);
            args.add(ttl);
            args.add(errorRate);
            args.add(capacity);
            for (Long postId : likedPostIds.subList(from, to)) {
                args.add(String.valueOf(postId));
            }
            stringRedisTemplate.execute(thumbBloomInitScript, List.of(key), args.toArray());
        }
    }

    /**
     * 记录一次帖子点赞计数变更。返回 1 表示累计变更已达刷库阈值。
     */
    public long markLikeCountDirty(Long postId) {
        Long signal = stringRedisTemplate.execute(
                thumbCountStatScript,
                List.of(
                        RedisKeyConstants.THUMB_LIKE_COUNT_DIRTY_SET,
                        RedisKeyConstants.THUMB_LIKE_COUNT_EVENT_COUNTER),
                String.valueOf(postId),
                String.valueOf(ThumbConstant.FLUSH_EVENT_THRESHOLD));
        return signal == null ? 0L : signal;
    }

    /** 仅当尚未安排 10 秒延迟刷库时占位成功 */
    public boolean tryScheduleDelayedFlush() {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyConstants.THUMB_LIKE_COUNT_FLUSH_SCHEDULED,
                "1",
                Duration.ofSeconds(10));
        return Boolean.TRUE.equals(ok);
    }

    public List<Long> takeDirtyPostIds() {
        List<String> raw = stringRedisTemplate.execute(
                thumbCountDrainScript,
                List.of(RedisKeyConstants.THUMB_LIKE_COUNT_DIRTY_SET));
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream().map(Long::valueOf).collect(Collectors.toList());
    }

    public void restoreDirtyPosts(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForSet().add(
                RedisKeyConstants.THUMB_LIKE_COUNT_DIRTY_SET,
                postIds.stream().map(String::valueOf).toArray(String[]::new));
    }

    /**
     * 同一帖子的刷库串行化：拿到锁才允许「读 Redis 计数 → 写 DB」。
     * 返回 null 表示别的实例正在刷这个帖子，调用方应把 postId 放回脏集合并稍后重试。
     */
    public String tryLockCountFlush(Long postId) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyConstants.buildThumbLikeCountFlushLockKey(postId),
                token,
                Duration.ofSeconds(ThumbConstant.COUNT_FLUSH_LOCK_TTL_SECONDS));
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    public void unlockCountFlush(Long postId, String token) {
        if (token == null) {
            return;
        }
        stringRedisTemplate.execute(
                countFlushUnlockScript,
                List.of(RedisKeyConstants.buildThumbLikeCountFlushLockKey(postId)),
                token);
    }

    public boolean isLikeCountDirty(Long postId) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                .isMember(RedisKeyConstants.THUMB_LIKE_COUNT_DIRTY_SET, String.valueOf(postId)));
    }

    /** 对账后覆盖展示计数。正在刷库的帖子不要写，避免盖掉还没落库的增量。 */
    public void writeReconciledCount(Long postId, long count) {
        stringRedisTemplate.opsForValue().set(
                RedisKeyManage.THUMB_COUNT.key(postId),
                String.valueOf(count),
                Duration.ofSeconds(ThumbConstant.COUNT_TTL_SECONDS));
    }

    /** Redis 里没有计数时返回 null，调用方改去数 tb_thumb */
    public Long readLikeCount(Long postId) {
        String value = stringRedisTemplate.opsForValue().get(RedisKeyManage.THUMB_COUNT.key(postId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
