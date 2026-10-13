package com.lucky.interaction.mq.consumer;

import com.lucky.interaction.constant.FollowConstant;
import com.lucky.interaction.entity.Follow;
import com.lucky.interaction.mapper.FollowMapper;
import com.lucky.interaction.mapper.UserInfoStatMapper;
import com.lucky.interaction.mq.message.FollowMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 关注关系落库：同一消费组按用户有序处理关注/取消，避免后发的删除被先到的插入覆盖。
 * 关注数和粉丝数都在关系真正插入或删除时加减。重复消息不再加减。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = FollowConstant.MQ_TOPIC,
        selectorExpression = FollowConstant.MQ_TAG + " || " + FollowConstant.MQ_UNFOLLOW_TAG,
        consumerGroup = FollowConstant.MQ_CONSUMER_GROUP,
        consumeMode = ConsumeMode.ORDERLY
)
public class FollowPersistConsumer implements RocketMQListener<FollowMessage> {

    private final FollowMapper followMapper;
    private final UserInfoStatMapper userInfoStatMapper;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(FollowMessage message) {
        if (FollowConstant.MQ_UNFOLLOW_TAG.equals(message.getAction())) {
            int deleted = followMapper.deleteByUserAndFollowee(message.getUserId(), message.getFollowUserId());
            if (deleted == 0) {
                log.info("关注关系已不存在，userId={}, followUserId={}",
                        message.getUserId(), message.getFollowUserId());
            } else {
                userInfoStatMapper.decreaseFollowee(message.getUserId());
                userInfoStatMapper.decreaseFans(message.getFollowUserId());
            }
            return;
        }
        Follow follow = new Follow();
        follow.setId(message.getId());
        follow.setUserId(message.getUserId());
        follow.setFollowUserId(message.getFollowUserId());
        follow.setCreateTime(toCreateTime(message.getFollowTime()));
        int inserted = followMapper.insertIgnore(follow);
        if (inserted == 0) {
            log.info("关注关系已存在，userId={}, followUserId={}",
                    message.getUserId(), message.getFollowUserId());
        } else {
            userInfoStatMapper.increaseFollowee(message.getUserId());
            userInfoStatMapper.increaseFans(message.getFollowUserId());
        }
    }

    private LocalDateTime toCreateTime(Long followTime) {
        if (followTime == null) {
            return LocalDateTime.now(clock);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(followTime), clock.getZone());
    }
}
