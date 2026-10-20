package com.lucky.interaction.service.impl;

import com.lucky.interaction.cache.ThumbCache;
import com.lucky.interaction.constant.ThumbConstant;
import com.lucky.interaction.mapper.PostCountMapper;
import com.lucky.interaction.mapper.ThumbMapper;
import com.lucky.interaction.mq.message.LikeCountFlushMessage;
import com.lucky.interaction.service.LikeCountFlushService;
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
public class LikeCountFlushServiceImpl implements LikeCountFlushService {

    private final ThumbCache thumbCache;
    private final PostCountMapper postCountMapper;
    private final ThumbMapper thumbMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void onLikeCountChanged(Long postId) {
        if (postId == null) {
            return;
        }
        long signal = thumbCache.markLikeCountDirty(postId);
        if (signal == 1L) {
            sendFlush(LikeCountFlushMessage.threshold(), 0);
            return;
        }
        if (thumbCache.tryScheduleDelayedFlush()) {
            sendFlush(LikeCountFlushMessage.timer(), ThumbConstant.MQ_DELAY_LEVEL_10S);
        }
    }

    @Override
    public void flushToDatabase() {
        List<Long> postIds = thumbCache.takeDirtyPostIds();
        if (postIds.isEmpty()) {
            return;
        }
        List<Long> skipped = new ArrayList<>();
        try {
            for (Long postId : postIds) {
                // 同一帖子按锁串行：「读 Redis 计数 → 写 DB」期间不允许别的实例插队，
                // 否则先读后写的旧值可能覆盖后读先写的新值。
                String lockToken = thumbCache.tryLockCountFlush(postId);
                if (lockToken == null) {
                    skipped.add(postId);
                    continue;
                }
                try {
                    Long redisCount = thumbCache.readLikeCount(postId);
                    long count = redisCount != null ? redisCount : thumbMapper.countByPostId(postId);
                    postCountMapper.upsertLikeCount(postId, count);
                } finally {
                    thumbCache.unlockCountFlush(postId, lockToken);
                }
            }
        } catch (RuntimeException e) {
            thumbCache.restoreDirtyPosts(postIds);
            throw e;
        }
        if (!skipped.isEmpty()) {
            // 锁被别的实例占用：放回脏集合，稍后再刷一次
            thumbCache.restoreDirtyPosts(skipped);
            if (thumbCache.tryScheduleDelayedFlush()) {
                sendFlush(LikeCountFlushMessage.timer(), ThumbConstant.MQ_DELAY_LEVEL_10S);
            }
        }
        log.info("帖子点赞数刷库完成，帖子数={}，跳过={}", postIds.size() - skipped.size(), skipped.size());
    }

    private void sendFlush(LikeCountFlushMessage body, int delayLevel) {
        SendResult sendResult;
        if (delayLevel > 0) {
            sendResult = rocketMQTemplate.syncSend(
                    ThumbConstant.MQ_FLUSH_DESTINATION,
                    MessageBuilder.withPayload(body).build(),
                    3000,
                    delayLevel);
        } else {
            sendResult = rocketMQTemplate.syncSend(ThumbConstant.MQ_FLUSH_DESTINATION, body);
        }
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("点赞数刷库消息发送失败：" +
                    (sendResult == null ? "null" : sendResult.getSendStatus()));
        }
    }
}
