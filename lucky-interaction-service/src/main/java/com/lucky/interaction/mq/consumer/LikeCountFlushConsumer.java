package com.lucky.interaction.mq.consumer;

import com.lucky.interaction.constant.ThumbConstant;
import com.lucky.interaction.mq.message.LikeCountFlushMessage;
import com.lucky.interaction.service.LikeCountFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = ThumbConstant.MQ_TOPIC,
        selectorExpression = ThumbConstant.MQ_FLUSH_TAG,
        consumerGroup = ThumbConstant.MQ_FLUSH_CONSUMER_GROUP)
public class LikeCountFlushConsumer implements RocketMQListener<LikeCountFlushMessage> {

    private final LikeCountFlushService likeCountFlushService;

    @Override
    public void onMessage(LikeCountFlushMessage message) {
        try {
            likeCountFlushService.flushToDatabase();
        } catch (RuntimeException e) {
            log.error("帖子点赞数刷库失败，reason={}", message == null ? null : message.getReason(), e);
            throw e;
        }
    }
}
