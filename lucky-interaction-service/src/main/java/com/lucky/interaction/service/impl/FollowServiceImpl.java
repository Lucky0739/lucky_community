package com.lucky.interaction.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.exception.BusinessException;
import com.lucky.interaction.cache.FollowCache;
import com.lucky.interaction.constant.FollowConstant;
import com.lucky.interaction.enums.FollowLuaResult;
import com.lucky.interaction.mapper.UserReadMapper;
import com.lucky.interaction.mq.FollowMessage;
import com.lucky.interaction.service.FollowCountFlushService;
import com.lucky.interaction.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final UserReadMapper userReadMapper;
    private final FollowCache followCache;
    private final FollowCountFlushService followCountFlushService;
    private final RocketMQTemplate rocketMQTemplate;
    private final Clock clock;

    @Override
    public void follow(Long userId, Long followUserId) {
        if (followUserId == null) {
            throw new BusinessException("用户不存在");
        }
        if (!userReadMapper.existsById(followUserId)) {
            throw new BusinessException("用户不存在");
        }
         // 时间戳
        long score = clock.millis();
        FollowLuaResult followLuaResult;
        try {
            followLuaResult = followCache.follow(userId, followUserId, score);
        } catch (RuntimeException e) {
            log.error("关注写入 Redis 失败，userId={}, followUserId={}", userId, followUserId, e);
            throw new BusinessException("关注失败，请稍后重试");
        }
        if (followLuaResult != FollowLuaResult.SUCCESS) {
            throw new BusinessException(followLuaResult == null ? "关注失败，请稍后重试" : followLuaResult.getErrorMsg());
        }
        /**
         * 写入消息
         */
        FollowMessage followMessage = new FollowMessage();
        followMessage.setId(IdUtil.getSnowflakeNextId());
        followMessage.setUserId(userId);
        followMessage.setFollowUserId(followUserId);
        followMessage.setFollowTime(score);
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(FollowConstant.MQ_DESTINATION, followMessage);
            if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("关注消息发送状态异常：" +
                        (sendResult == null ? "null" : sendResult.getSendStatus()));
            }
        } catch (RuntimeException e) {
            followCache.rollback(userId, followUserId);
            log.error("关注消息发送失败，已回滚 Redis，userId={}, followUserId={}", userId, followUserId, e);
            throw new BusinessException("关注失败，请稍后重试");
        }

        try {
            followCountFlushService.onFollowed(userId);
        } catch (RuntimeException e) {
            log.error("关注数刷库窗口更新失败，将等待下次关注或延迟消息，userId={}", userId, e);
        }
    }
}
