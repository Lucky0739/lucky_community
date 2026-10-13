package com.lucky.comment.mq;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.comment.constant.CommentMqConstant;
import com.lucky.comment.entity.CommentMqOutbox;
import com.lucky.comment.mapper.CommentMqOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 同步发评论消息。失败后等 1 秒、2 秒、4 秒再试，一共再试 3 次。
 * 还是失败就把消息写入兜底表，定时任务以后补发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentMqSender {

    private final RocketMQTemplate rocketMQTemplate;
    private final CommentMqOutboxMapper commentMqOutboxMapper;
    private final ObjectMapper objectMapper;

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void send(PublishCommentMessage message) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(message);
        rocketMQTemplate.syncSend(
                CommentMqConstant.MQ_DESTINATION,
                MessageBuilder.withPayload(json).build());
    }

    @Recover
    public void saveOutbox(Exception exception, PublishCommentMessage message) throws JsonProcessingException {
        log.error("评论消息发送多次失败，先写入兜底表，commentId={}", message.getId(), exception);
        CommentMqOutbox row = new CommentMqOutbox();
        row.setId(IdUtil.getSnowflakeNextId());
        row.setTopic(CommentMqConstant.MQ_DESTINATION);
        row.setPayload(objectMapper.writeValueAsString(message));
        commentMqOutboxMapper.insert(row);
    }
}
