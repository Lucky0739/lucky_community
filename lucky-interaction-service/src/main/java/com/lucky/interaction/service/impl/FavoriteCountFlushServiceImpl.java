package com.lucky.interaction.service.impl;

import com.lucky.interaction.cache.FavoriteCache;
import com.lucky.interaction.constant.FavoriteConstant;
import com.lucky.interaction.mapper.FavoriteMapper;
import com.lucky.interaction.mapper.PostCountMapper;
import com.lucky.interaction.mq.message.FavoriteCountFlushMessage;
import com.lucky.interaction.service.FavoriteCountFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteCountFlushServiceImpl implements FavoriteCountFlushService {

    private final FavoriteCache favoriteCache;
    private final PostCountMapper postCountMapper;
    private final FavoriteMapper favoriteMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void onFavoriteCountChanged(Long postId) {
        if (postId == null) {
            return;
        }
        long signal = favoriteCache.markFavoriteCountDirty(postId);
        if (signal == 1L) {
            sendFlush(FavoriteCountFlushMessage.threshold(), 0);
            return;
        }
        if (favoriteCache.tryScheduleDelayedFlush()) {
            sendFlush(FavoriteCountFlushMessage.timer(), FavoriteConstant.MQ_DELAY_LEVEL_10S);
        }
    }

    @Override
    public void flushToDatabase() {
        List<Long> postIds = favoriteCache.takeDirtyPostIds();
        if (postIds.isEmpty()) {
            return;
        }
        List<Long> skipped = new ArrayList<>();
        try {
            for (Long postId : postIds) {
                // 同一帖子按锁串行：「读 Redis 计数 → 写 DB」期间不允许别的实例插队，
                // 否则先读后写的旧值可能覆盖后读先写的新值。
                String lockToken = favoriteCache.tryLockCountFlush(postId);
                if (lockToken == null) {
                    skipped.add(postId);
                    continue;
                }
                try {
                    Long redisCount = favoriteCache.readFavoriteCount(postId);
                    long count = redisCount != null ? redisCount : favoriteMapper.countByPostId(postId);
                    postCountMapper.upsertFavoriteCount(postId, count);
                } finally {
                    favoriteCache.unlockCountFlush(postId, lockToken);
                }
            }
        } catch (RuntimeException e) {
            favoriteCache.restoreDirtyPosts(postIds);
            throw e;
        }
        if (!skipped.isEmpty()) {
            // 锁被别的实例占用：放回脏集合，稍后再刷一次
            favoriteCache.restoreDirtyPosts(skipped);
            if (favoriteCache.tryScheduleDelayedFlush()) {
                sendFlush(FavoriteCountFlushMessage.timer(), FavoriteConstant.MQ_DELAY_LEVEL_10S);
            }
        }
        log.info("帖子收藏数刷库完成，帖子数={}，跳过={}", postIds.size() - skipped.size(), skipped.size());
    }

    private void sendFlush(FavoriteCountFlushMessage body, int delayLevel) {
        SendResult sendResult;
        if (delayLevel > 0) {
            sendResult = rocketMQTemplate.syncSend(
                    FavoriteConstant.MQ_FLUSH_DESTINATION,
                    MessageBuilder.withPayload(body).build(),
                    3000,
                    delayLevel);
        } else {
            sendResult = rocketMQTemplate.syncSend(FavoriteConstant.MQ_FLUSH_DESTINATION, body);
        }
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("收藏数刷库消息发送失败：" +
                    (sendResult == null ? "null" : sendResult.getSendStatus()));
        }
    }
}
