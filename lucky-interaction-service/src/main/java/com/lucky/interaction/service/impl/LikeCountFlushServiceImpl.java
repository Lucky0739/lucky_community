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
        try {
            for (Long postId : postIds) {
                Long redisCount = thumbCache.readLikeCount(postId);
                long count = redisCount != null ? redisCount : thumbMapper.countByPostId(postId);
                postCountMapper.upsertLikeCount(postId, count);
            }
            log.info("帖子点赞数刷库完成，帖子数={}", postIds.size());
        } catch (RuntimeException e) {
            thumbCache.restoreDirtyPosts(postIds);
            throw e;
        }
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
