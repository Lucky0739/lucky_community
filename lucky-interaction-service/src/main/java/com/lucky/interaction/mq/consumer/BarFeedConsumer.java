package com.lucky.interaction.mq.consumer;

import com.lucky.common.constant.BarFeedMqConstant;
import com.lucky.common.mq.BarFeedMessage;
import com.lucky.interaction.service.FollowFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = BarFeedMqConstant.MQ_TOPIC,
        selectorExpression = BarFeedMqConstant.MQ_TAG,
        consumerGroup = BarFeedMqConstant.MQ_CONSUMER_GROUP)
public class BarFeedConsumer implements RocketMQListener<BarFeedMessage> {

    private final FollowFeedService followFeedService;

    @Override
    public void onMessage(BarFeedMessage message) {
        if (message == null || message.getBarId() == null || message.getPostId() == null) {
            return;
        }
        long createTime = message.getCreateTime() == null ? System.currentTimeMillis() : message.getCreateTime();
        followFeedService.onPostPublished(message.getBarId(), message.getPostId(), createTime);
    }
}
