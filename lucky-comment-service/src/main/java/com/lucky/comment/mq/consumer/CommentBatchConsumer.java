package com.lucky.comment.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.lucky.common.mq.RocketMqNameServer;
import com.lucky.comment.constant.CommentMqConstant;
import com.lucky.comment.mq.message.PublishCommentMessage;
import com.lucky.comment.service.CommentService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 评论批量写库。每批最多 30 条，整批落库成功后才确认。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentBatchConsumer {

    private final CommentService commentService;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private DefaultMQPushConsumer consumer;

    /** 每秒最多放行 300 个批次 */
    private final RateLimiter rateLimiter = RateLimiter.create(300);

    @Bean
    public DefaultMQPushConsumer mqPushConsumer() throws MQClientException {
        consumer = new DefaultMQPushConsumer(CommentMqConstant.MQ_CONSUMER_GROUP);
        RocketMqNameServer.apply(consumer, namesrvAddr);
        consumer.subscribe(CommentMqConstant.MQ_TOPIC, CommentMqConstant.MQ_TAG);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(CommentMqConstant.CONSUME_BATCH_MAX);
        consumer.setPullBatchSize(CommentMqConstant.CONSUME_BATCH_MAX);

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            log.info("本批次消息大小: {}", msgs.size());
            try {
                rateLimiter.acquire();
                List<PublishCommentMessage> messages = new ArrayList<>();
                for (MessageExt msg : msgs) {
                    String json = new String(msg.getBody(), StandardCharsets.UTF_8);
                    try {
                        PublishCommentMessage message = objectMapper.readValue(json, PublishCommentMessage.class);
                        log.info("收到评论消息，commentId={}，msgId={}", message.getId(), msg.getMsgId());
                        messages.add(message);
                    } catch (Exception e) {
                        log.error("评论消息无法解析，已跳过，msgId={}", msg.getMsgId(), e);
                    }
                }
                if (!messages.isEmpty()) {
                    List<PublishCommentMessage> fresh = commentService.saveBatch(messages);
                    commentService.addHeat(fresh);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                log.error("评论批量落库失败，稍后重试，size={}", msgs.size(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });

        consumer.start();
        return consumer;
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("关闭评论消费者失败", e);
            }
        }
    }
}
