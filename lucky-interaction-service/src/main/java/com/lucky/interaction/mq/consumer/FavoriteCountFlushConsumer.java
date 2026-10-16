package com.lucky.interaction.mq.consumer;

import com.lucky.interaction.constant.FavoriteConstant;
import com.lucky.interaction.mq.message.FavoriteCountFlushMessage;
import com.lucky.interaction.service.FavoriteCountFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = FavoriteConstant.MQ_TOPIC,
        selectorExpression = FavoriteConstant.MQ_FLUSH_TAG,
        consumerGroup = FavoriteConstant.MQ_FLUSH_CONSUMER_GROUP)
public class FavoriteCountFlushConsumer implements RocketMQListener<FavoriteCountFlushMessage> {

    private final FavoriteCountFlushService favoriteCountFlushService;

    @Override
    public void onMessage(FavoriteCountFlushMessage message) {
        try {
            favoriteCountFlushService.flushToDatabase();
        } catch (RuntimeException e) {
            log.error("帖子收藏数刷库失败，reason={}", message == null ? null : message.getReason(), e);
            throw e;
        }
    }
}
