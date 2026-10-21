package com.lucky.community.mq;

import com.lucky.common.constant.BarFeedMqConstant;
import com.lucky.common.mq.BarFeedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 新帖通知关注流。失败只记日志，不回滚已经发出的帖子。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BarFeedMqSender {

    private final RocketMQTemplate rocketMQTemplate;

    public void send(Long postId, Long barId, LocalDateTime createTime) {
        BarFeedMessage message = new BarFeedMessage();
        message.setPostId(postId);
        message.setBarId(barId);
        message.setCreateTime(createTime == null
                ? System.currentTimeMillis()
                : createTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        try {
            rocketMQTemplate.syncSend(BarFeedMqConstant.MQ_DESTINATION, message);
        } catch (RuntimeException e) {
            log.error("关注流消息发送失败，postId={}，barId={}", postId, barId, e);
        }
    }
}
