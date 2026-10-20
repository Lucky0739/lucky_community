package com.lucky.interaction.schedule;

import com.lucky.interaction.cache.FavoriteCache;
import com.lucky.interaction.cache.ThumbCache;
import com.lucky.interaction.mapper.PostCountMapper;
import com.lucky.interaction.support.entity.PostCountSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 每天按主键游标分批对账。帖子走 tb_post.id。
 * 每一页只统计 (cursor, untilId] 内的明细，正在刷库的帖子不回写 Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngagementCountReconcileJob {

    private static final String LOCK_KEY = "engagement:count:reconcile:lock";

    private final PostCountMapper postCountMapper;
    private final ThumbCache thumbCache;
    private final FavoriteCache favoriteCache;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${engagement.reconcile.batch-size:500}")
    private int batchSize;

    @Scheduled(cron = "${engagement.reconcile.cron:0 30 4 * * *}", zone = "Asia/Shanghai")
    public void reconcile() {
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofHours(2));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("计数对账已有实例在执行，跳过");
            return;
        }
        int size = Math.min(Math.max(batchSize, 1), 2000);
        try {
            int[] posts = reconcilePosts(size);
            log.info("计数对账完成，扫描帖子={}，帖子计数变更={}，回写点赞缓存={}，回写收藏缓存={}",
                    posts[0], posts[1], posts[2], posts[3]);
        } catch (RuntimeException e) {
            log.error("计数对账失败", e);
        }
    }

    /** @return [扫描帖子数, 计数变更数, 点赞缓存回写数, 收藏缓存回写数] */
    private int[] reconcilePosts(int size) {
        long cursor = 0L;
        int scanned = 0;
        int changed = 0;
        int likeRedis = 0;
        int favoriteRedis = 0;
        while (true) {
            List<Long> postIds = postCountMapper.selectPostIdsAfter(cursor, size);
            if (postIds == null || postIds.isEmpty()) {
                break;
            }
            long untilId = postIds.get(postIds.size() - 1);
            List<PostCountSnapshot> rows = postCountMapper.reconcilePostRange(cursor, untilId);
            scanned += postIds.size();
            if (rows != null) {
                changed += rows.size();
                for (PostCountSnapshot row : rows) {
                    if (row.getPostId() == null) {
                        continue;
                    }
                    long likes = row.getLikeCount() == null ? 0L : row.getLikeCount();
                    long favorites = row.getFavoriteCount() == null ? 0L : row.getFavoriteCount();
                    if (!thumbCache.isLikeCountDirty(row.getPostId())) {
                        thumbCache.writeReconciledCount(row.getPostId(), likes);
                        likeRedis++;
                    }
                    if (!favoriteCache.isFavoriteCountDirty(row.getPostId())) {
                        favoriteCache.writeReconciledCount(row.getPostId(), favorites);
                        favoriteRedis++;
                    }
                }
            }
            cursor = untilId;
            if (postIds.size() < size) {
                break;
            }
        }
        return new int[]{scanned, changed, likeRedis, favoriteRedis};
    }
}
