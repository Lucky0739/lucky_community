package com.lucky.interaction.cache;

import com.lucky.interaction.constant.FavoriteConstant;
import com.lucky.interaction.constant.RedisKeyConstants;
import com.lucky.interaction.enums.FavoriteLuaResult;
import com.lucky.interaction.enums.FavoriteUnfavoriteLuaResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FavoriteCache {

    private static final int MADD_BATCH = 500;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> thumbLikeScript;
    private final DefaultRedisScript<Long> thumbBloomInitScript;
    private final DefaultRedisScript<Long> thumbUnlikeScript;
    private final DefaultRedisScript<Long> thumbUnlikeRollbackScript;
    private final DefaultRedisScript<Long> thumbCountStatScript;
    private final DefaultRedisScript<List<String>> thumbCountDrainScript;

    public FavoriteCache(StringRedisTemplate stringRedisTemplate,
                         @Qualifier("thumbLikeScript") DefaultRedisScript<Long> thumbLikeScript,
                         @Qualifier("thumbBloomInitScript") DefaultRedisScript<Long> thumbBloomInitScript,
                         @Qualifier("thumbUnlikeScript") DefaultRedisScript<Long> thumbUnlikeScript,
                         @Qualifier("thumbUnlikeRollbackScript") DefaultRedisScript<Long> thumbUnlikeRollbackScript,
                         @Qualifier("thumbCountStatScript") DefaultRedisScript<Long> thumbCountStatScript,
                         @Qualifier("thumbCountDrainScript") DefaultRedisScript<List<String>> thumbCountDrainScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.thumbLikeScript = thumbLikeScript;
        this.thumbBloomInitScript = thumbBloomInitScript;
        this.thumbUnlikeScript = thumbUnlikeScript;
        this.thumbUnlikeRollbackScript = thumbUnlikeRollbackScript;
        this.thumbCountStatScript = thumbCountStatScript;
        this.thumbCountDrainScript = thumbCountDrainScript;
    }

    public void preloadCountIfAbsent(Long postId, long dbCount) {
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyConstants.buildFavoriteCountKey(postId),
                String.valueOf(dbCount),
                Duration.ofSeconds(FavoriteConstant.COUNT_TTL_SECONDS));
    }

    public FavoriteLuaResult favorite(Long userId, Long postId) {
        Long code = stringRedisTemplate.execute(
                thumbLikeScript,
                favoriteKeys(userId, postId),
                String.valueOf(postId),
                String.valueOf(FavoriteConstant.BLOOM_TTL_SECONDS),
                String.valueOf(FavoriteConstant.DATA_TTL_SECONDS),
                String.valueOf(FavoriteConstant.COUNT_TTL_SECONDS));
        return FavoriteLuaResult.fromCode(code);
    }

    public FavoriteUnfavoriteLuaResult unfavorite(Long userId, Long postId, boolean dbConfirmed) {
        Long code = stringRedisTemplate.execute(
                thumbUnlikeScript,
                favoriteKeys(userId, postId),
                String.valueOf(postId),
                String.valueOf(FavoriteConstant.BLOOM_TTL_SECONDS),
                String.valueOf(FavoriteConstant.DATA_TTL_SECONDS),
                String.valueOf(FavoriteConstant.COUNT_TTL_SECONDS),
                dbConfirmed ? "1" : "0");
        return FavoriteUnfavoriteLuaResult.fromCode(code);
    }

    public void rollbackUnfavorite(Long userId, Long postId) {
        stringRedisTemplate.execute(
                thumbUnlikeRollbackScript,
                favoriteKeys(userId, postId),
                String.valueOf(postId),
                String.valueOf(FavoriteConstant.BLOOM_TTL_SECONDS),
                String.valueOf(FavoriteConstant.DATA_TTL_SECONDS),
                String.valueOf(FavoriteConstant.COUNT_TTL_SECONDS));
    }

    private List<String> favoriteKeys(Long userId, Long postId) {
        return List.of(
                RedisKeyConstants.buildBloomUserPostFavoriteListKey(userId),
                RedisKeyConstants.buildFavoriteUserKey(userId),
                RedisKeyConstants.buildFavoriteCountKey(postId),
                RedisKeyConstants.buildFavoriteCancelKey(userId));
    }

    public boolean hasCount(Long postId) {
        return stringRedisTemplate.hasKey(RedisKeyConstants.buildFavoriteCountKey(postId));
    }

    public void markUnsynced(Long userId, Long postId, Long favoriteId) {
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstants.buildFavoriteUnsyncedKey(userId, postId),
                String.valueOf(favoriteId),
                Duration.ofDays(1));
    }

    public Long unsyncedId(Long userId, Long postId) {
        String value = stringRedisTemplate.opsForValue().get(
                RedisKeyConstants.buildFavoriteUnsyncedKey(userId, postId));
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
        stringRedisTemplate.delete(RedisKeyConstants.buildFavoriteUnsyncedKey(userId, postId));
    }

    public void initBloom(Long userId, List<Long> favoritedPostIds) {
        String key = RedisKeyConstants.buildBloomUserPostFavoriteListKey(userId);
        String ttl = String.valueOf(FavoriteConstant.BLOOM_TTL_SECONDS);
        String errorRate = FavoriteConstant.BLOOM_ERROR_RATE;
        String capacity = String.valueOf(FavoriteConstant.BLOOM_CAPACITY);
        if (favoritedPostIds == null || favoritedPostIds.isEmpty()) {
            stringRedisTemplate.execute(thumbBloomInitScript, List.of(key), ttl, errorRate, capacity);
            return;
        }
        for (int from = 0; from < favoritedPostIds.size(); from += MADD_BATCH) {
            int to = Math.min(from + MADD_BATCH, favoritedPostIds.size());
            List<String> args = new ArrayList<>(to - from + 3);
            args.add(ttl);
            args.add(errorRate);
            args.add(capacity);
            for (Long pid : favoritedPostIds.subList(from, to)) {
                args.add(String.valueOf(pid));
            }
            stringRedisTemplate.execute(thumbBloomInitScript, List.of(key), args.toArray());
        }
    }

    public long markFavoriteCountDirty(Long postId) {
        Long signal = stringRedisTemplate.execute(
                thumbCountStatScript,
                List.of(
                        RedisKeyConstants.FAVORITE_COUNT_DIRTY_SET,
                        RedisKeyConstants.FAVORITE_COUNT_EVENT_COUNTER),
                String.valueOf(postId),
                String.valueOf(FavoriteConstant.FLUSH_EVENT_THRESHOLD));
        return signal == null ? 0L : signal;
    }

    public boolean tryScheduleDelayedFlush() {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyConstants.FAVORITE_COUNT_FLUSH_SCHEDULED,
                "1",
                Duration.ofSeconds(10));
        return Boolean.TRUE.equals(ok);
    }

    public List<Long> takeDirtyPostIds() {
        List<String> raw = stringRedisTemplate.execute(
                thumbCountDrainScript,
                List.of(RedisKeyConstants.FAVORITE_COUNT_DIRTY_SET));
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
                RedisKeyConstants.FAVORITE_COUNT_DIRTY_SET,
                postIds.stream().map(String::valueOf).toArray(String[]::new));
    }

    public Long readFavoriteCount(Long postId) {
        String value = stringRedisTemplate.opsForValue().get(RedisKeyConstants.buildFavoriteCountKey(postId));
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
