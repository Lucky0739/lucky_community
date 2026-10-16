package com.lucky.search.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.common.constant.PostIndexMqConstant;
import com.lucky.common.mq.PostIndexMessage;
import com.lucky.search.service.PostIndexService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 消费 post-service 发来的帖子索引事件，写 / 删 ES 文档。
 *
 * <p>整批成功才 ack：中途抛异常就 {@code RECONSUME_LATER} 重投。因为
 * {@link PostIndexService} 的写入是按 postId 幂等的，重投不会写出重复文档。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostIndexConsumer {

    private final PostIndexService postIndexService;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private DefaultMQPushConsumer consumer;

    @Bean
    public DefaultMQPushConsumer postIndexPushConsumer() throws Exception {
        consumer = new DefaultMQPushConsumer(PostIndexMqConstant.MQ_CONSUMER_GROUP);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.subscribe(PostIndexMqConstant.MQ_TOPIC, PostIndexMqConstant.MQ_TAG);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(PostIndexMqConstant.CONSUME_BATCH_MAX);
        consumer.setPullBatchSize(PostIndexMqConstant.CONSUME_BATCH_MAX);

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            try {
                for (MessageExt msg : msgs) {
                    String json = new String(msg.getBody(), StandardCharsets.UTF_8);
                    PostIndexMessage message;
                    try {
                        message = objectMapper.readValue(json, PostIndexMessage.class);
                    } catch (Exception e) {
                        // 解析不了的消息重投多少次都一样，跳过而不是卡住整个队列
                        log.error("帖子索引消息无法解析，已跳过，msgId={}", msg.getMsgId(), e);
                        continue;
                    }
                    postIndexService.apply(message);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                log.error("帖子索引本批处理失败，稍后重投，size={}", msgs.size(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });

        consumer.start();
        log.info("帖子索引消费者已启动，topic={}", PostIndexMqConstant.MQ_TOPIC);
        return consumer;
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("关闭帖子索引消费者失败", e);
            }
        }
    }
}
