package com.lucky.comment.mq;

import com.lucky.comment.entity.CommentMqOutbox;
import com.lucky.comment.mapper.CommentMqOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把发送失败的评论消息再发出去。成功后删掉兜底记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentMqOutboxJob {

    private final CommentMqOutboxMapper commentMqOutboxMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelay = 5000)
    public void resend() {
        List<CommentMqOutbox> rows = commentMqOutboxMapper.selectOldest(20);
        for (CommentMqOutbox row : rows) {
            try {
                rocketMQTemplate.syncSend(row.getTopic(), MessageBuilder.withPayload(row.getPayload()).build());
                commentMqOutboxMapper.deleteById(row.getId());
            } catch (RuntimeException e) {
                log.warn("评论兜底消息补发失败，下次再试，id={}", row.getId(), e);
            }
        }
    }
}
