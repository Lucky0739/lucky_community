package com.lucky.interaction.mq;

import com.lucky.interaction.constant.FollowConstant;
import com.lucky.interaction.entity.Follow;
import com.lucky.interaction.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 关注关系落库：只写 tb_follow。user_info.followee 由 10 秒 / 500 次窗口另刷。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = FollowConstant.MQ_TOPIC,
        selectorExpression = FollowConstant.MQ_TAG,
        consumerGroup = FollowConstant.MQ_CONSUMER_GROUP
)
public class FollowPersistConsumer implements RocketMQListener<FollowMessage> {

    private final FollowMapper followMapper;
    private final Clock clock;

    @Override
    public void onMessage(FollowMessage message) {
        Follow follow = new Follow();
        follow.setId(message.getId());
        follow.setUserId(message.getUserId());
        follow.setFollowUserId(message.getFollowUserId());
        follow.setCreateTime(toCreateTime(message.getFollowTime()));
        int inserted = followMapper.insertIgnore(follow);
        if (inserted == 0) {
            log.info("关注关系已存在，userId={}, followUserId={}",
                    message.getUserId(), message.getFollowUserId());
        }
    }

    private LocalDateTime toCreateTime(Long followTime) {
        if (followTime == null) {
            return LocalDateTime.now(clock);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(followTime), clock.getZone());
    }
}
