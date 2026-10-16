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
        try {
            for (Long postId : postIds) {
                Long redisCount = favoriteCache.readFavoriteCount(postId);
                long count = redisCount != null ? redisCount : favoriteMapper.countByPostId(postId);
                postCountMapper.upsertFavoriteCount(postId, count);
            }
            log.info("帖子收藏数刷库完成，帖子数={}", postIds.size());
        } catch (RuntimeException e) {
            favoriteCache.restoreDirtyPosts(postIds);
            throw e;
        }
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
