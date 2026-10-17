package com.lucky.comment.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.lucky.comment.constant.CommentMqConstant;
import com.lucky.comment.entity.Comment;
import com.lucky.comment.mq.message.DeleteCommentMessage;
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
import java.util.List;
import java.util.Objects;

/**
 * 删除评论的消费者。和 PUBLISH 共用 comment-topic，靠 tag 区分，独立消费组
 * （组名不同才不会和 {@link CommentBatchConsumer} 互相瓜分消息）。
 * 每批最多 30 条；整批成功才确认，失败重投——重投是安全的，
 * 删除本身幂等（见 {@code CommentService#handleDelete}）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentDeleteConsumer {

    private final CommentService commentService;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private DefaultMQPushConsumer consumer;

    /** 每秒最多放行 300 个批次 */
    private final RateLimiter rateLimiter = RateLimiter.create(300);

    @Bean
    public DefaultMQPushConsumer commentDeletePushConsumer() throws MQClientException {
        consumer = new DefaultMQPushConsumer(CommentMqConstant.MQ_DELETE_CONSUMER_GROUP);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.subscribe(CommentMqConstant.MQ_TOPIC, CommentMqConstant.MQ_DELETE_TAG);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(CommentMqConstant.CONSUME_BATCH_MAX);
        consumer.setPullBatchSize(CommentMqConstant.CONSUME_BATCH_MAX);

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            log.info("本批次待删除评论消息大小: {}", msgs.size());
            try {
                rateLimiter.acquire();
                for (MessageExt msg : msgs) {
                    DeleteCommentMessage message;
                    try {
                        message = objectMapper.readValue(
                                new String(msg.getBody(), StandardCharsets.UTF_8), DeleteCommentMessage.class);
                    } catch (Exception e) {
                        log.error("删除评论消息无法解析，已跳过，msgId={}", msg.getMsgId(), e);
                        continue;
                    }
                    // 事务在这里面提交；正文删除放在事务外做
                    List<Comment> contentsToDelete = commentService.handleDelete(message);
                    commentService.deleteContents(contentsToDelete);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                log.error("删除评论失败，稍后重试，size={}", msgs.size(), e);
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
                log.error("关闭删除评论消费者失败", e);
            }
        }
    }
}
