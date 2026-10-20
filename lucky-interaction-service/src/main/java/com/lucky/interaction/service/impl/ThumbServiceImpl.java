package com.lucky.interaction.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.exception.BusinessException;
import com.lucky.interaction.cache.PostExistenceCache;
import com.lucky.interaction.cache.ThumbCache;
import com.lucky.interaction.constant.ThumbConstant;
import com.lucky.interaction.enums.ThumbLuaResult;
import com.lucky.interaction.enums.ThumbUnlikeLuaResult;
import com.lucky.interaction.mapper.ThumbMapper;
import com.lucky.interaction.mq.message.ThumbMessage;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.service.LikeCountFlushService;
import com.lucky.interaction.service.ThumbService;
import com.lucky.interaction.service.UserInteractionPostQueryService;
import com.lucky.interaction.vo.UserPostSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbServiceImpl implements ThumbService {

    private final PostExistenceCache postExistenceCache;
    private final ThumbCache thumbCache;
    private final ThumbMapper thumbMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final Clock clock;
    private final LikeCountFlushService likeCountFlushService;
    private final UserInteractionPostQueryService userInteractionPostQueryService;

    /**
     * 点赞功能业务逻辑
     * @param userId
     * @param postId
     */
    @Override
    public void like(Long userId, Long postId) {
        // 传入postId为空
        if (postId == null) {
            throw new BusinessException("没有传入帖子ID");
        }
        // 查本地缓存Caffeine和redis的PostId帖子信息
        if (!postExistenceCache.exists(postId)) {
            throw new BusinessException("帖子不存在");
        }

        if (!thumbCache.hasCount(postId)) {
            thumbCache.preloadCountIfAbsent(postId, thumbMapper.countByPostId(postId));
        }
        // 执行Lua脚本
        ThumbLuaResult thumbLuaResult = executeLike(userId, postId);
        // 布隆过滤器中postId不存在
        if (thumbLuaResult == ThumbLuaResult.BLOOM_ABSENT) {
            // 查询数据库用户的点赞帖子id列表
            List<Long> likedPostIds = thumbMapper.selectPostIdsByUserId(userId);
            try {
                thumbCache.initBloom(userId, likedPostIds);
            } catch (RuntimeException e) {
                log.error("初始化点赞布隆过滤器失败，userId={}", userId, e);
                throw new BusinessException("点赞失败，请稍后重试");
            }
            thumbLuaResult = executeLike(userId, postId);
        }
        if (thumbLuaResult == ThumbLuaResult.ALREADY) {
            resendIfUnsynced(userId, postId);
            throw new BusinessException(thumbLuaResult.getErrorMsg());
        }
        if (thumbLuaResult != ThumbLuaResult.SUCCESS) {
            throw new BusinessException(thumbLuaResult == null ? "点赞失败，请稍后重试" : thumbLuaResult.getErrorMsg());
        }
        likeCountFlushService.onLikeCountChanged(postId);
        // thumbId使用雪花算法
        long thumbId = IdUtil.getSnowflakeNextId();
        // 点赞成功待落库标记
        thumbCache.markUnsynced(userId, postId, thumbId);
        try {
            // 发送消息给RocketMQ
            sendPersist(userId, postId, thumbId, clock.millis());
        } catch (RuntimeException e) {
            log.error("点赞消息发送失败，Redis 中的点赞保留，等待重试补写数据库，userId={}, postId={}",
                    userId, postId, e);
            throw new BusinessException("点赞失败，请稍后重试");
        }
    }

    /**
     * 取消点赞。帖子必须存在，且当前用户确实点过。Redis 先删，再发消息删 tb_thumb。
     */
    @Override
    public void unlike(Long userId, Long postId) {
        if (postId == null) {
            throw new BusinessException("没有传入帖子ID");
        }
        if (!postExistenceCache.exists(postId)) {
            throw new BusinessException("帖子不存在");
        }
        if (!thumbCache.hasCount(postId)) {
            thumbCache.preloadCountIfAbsent(postId, thumbMapper.countByPostId(postId));
        }

        ThumbUnlikeLuaResult thumbUnlikeLuaResult = prepareAndUnlike(userId, postId);
        if (thumbUnlikeLuaResult == ThumbUnlikeLuaResult.NEED_DB) {
            if (!thumbMapper.exists(userId, postId)) {
                throw new BusinessException(ThumbUnlikeLuaResult.NOT_LIKED.getErrorMsg());
            }
            thumbUnlikeLuaResult = executeUnlike(userId, postId, true);
            if (thumbUnlikeLuaResult == ThumbUnlikeLuaResult.CONFLICT) {
                thumbUnlikeLuaResult = executeUnlike(userId, postId, false);
            }
        }
        if (thumbUnlikeLuaResult == ThumbUnlikeLuaResult.NOT_LIKED) {
            throw new BusinessException(thumbUnlikeLuaResult.getErrorMsg());
        }
        if (thumbUnlikeLuaResult != ThumbUnlikeLuaResult.SUCCESS) {
            throw new BusinessException(thumbUnlikeLuaResult == null || thumbUnlikeLuaResult.getErrorMsg() == null
                    ? "取消点赞失败，请稍后重试" : thumbUnlikeLuaResult.getErrorMsg());
        }
        likeCountFlushService.onLikeCountChanged(postId);

        try {
            sendUnlike(userId, postId);
        } catch (RuntimeException e) {
            try {
                thumbCache.rollbackUnlike(userId, postId);
                likeCountFlushService.onLikeCountChanged(postId);
            } catch (RuntimeException rollbackEx) {
                log.error("取消点赞回滚 Redis 失败，userId={}, postId={}", userId, postId, rollbackEx);
            }
            log.error("取消点赞消息发送失败，已回滚 Redis，userId={}, postId={}", userId, postId, e);
            throw new BusinessException("取消点赞失败，请稍后重试");
        }
        thumbCache.clearUnsynced(userId, postId);
    }

    private ThumbUnlikeLuaResult prepareAndUnlike(Long userId, Long postId) {
        ThumbUnlikeLuaResult result = executeUnlike(userId, postId, false);
        if (result != ThumbUnlikeLuaResult.BLOOM_ABSENT) {
            return result;
        }
        List<Long> likedPostIds = thumbMapper.selectPostIdsByUserId(userId);
        try {
            thumbCache.initBloom(userId, likedPostIds);
        } catch (RuntimeException e) {
            log.error("初始化点赞布隆过滤器失败，userId={}", userId, e);
            throw new BusinessException("取消点赞失败，请稍后重试");
        }
        return executeUnlike(userId, postId, false);
    }

    private void sendUnlike(Long userId, Long postId) {
        ThumbMessage message = new ThumbMessage();
        message.setUserId(userId);
        message.setPostId(postId);
        message.setAction(ThumbConstant.MQ_UNLIKE_TAG);
        SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                ThumbConstant.MQ_UNLIKE_DESTINATION, message, String.valueOf(userId));
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("取消点赞消息发送状态异常：" +
                    (sendResult == null ? "null" : sendResult.getSendStatus()));
        }
    }

    private ThumbUnlikeLuaResult executeUnlike(Long userId, Long postId, boolean dbConfirmed) {
        try {
            return thumbCache.unlike(userId, postId, dbConfirmed);
        } catch (RuntimeException e) {
            log.error("取消点赞写入 Redis 失败，userId={}, postId={}", userId, postId, e);
            throw new BusinessException("取消点赞失败，请稍后重试");
        }
    }

    private void resendIfUnsynced(Long userId, Long postId) {
        Long thumbId = thumbCache.unsyncedId(userId, postId);
        if (thumbId == null) {
            return;
        }
        try {
            sendPersist(userId, postId, thumbId, clock.millis());
            thumbCache.clearUnsynced(userId, postId);
        } catch (RuntimeException e) {
            log.error("补发点赞落库消息失败，userId={}, postId={}", userId, postId, e);
        }
    }

    // 发送消息给消息队列
    private void sendPersist(Long userId, Long postId, long thumbId, long thumbTime) {
        ThumbMessage thumbMessage = new ThumbMessage();
        thumbMessage.setId(thumbId);
        thumbMessage.setUserId(userId);
        thumbMessage.setPostId(postId);
        thumbMessage.setThumbTime(thumbTime);
        // 显式标注意图
        thumbMessage.setAction(ThumbConstant.MQ_TAG);
        // 发送顺序消息到RocketMQ，传入目标地址、点赞消息体和分区键（userId）
        SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                ThumbConstant.MQ_DESTINATION, thumbMessage, String.valueOf(userId));
        // 发送失败/异常
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("点赞消息发送状态异常：" +
                    (sendResult == null ? "null" : sendResult.getSendStatus()));
        }
    }

    @Override
    public PageResult<UserPostSummaryVO> pageLikedPosts(Long viewerId, UserPostListQueryDTO query) {
        return userInteractionPostQueryService.pageLikedPosts(viewerId, query);
    }

    private ThumbLuaResult executeLike(Long userId, Long postId) {
        try {
            return thumbCache.like(userId, postId);
        } catch (RuntimeException e) {
            log.error("点赞写入 Redis 失败，userId={}, postId={}", userId, postId, e);
            throw new BusinessException("点赞失败，请稍后重试");
        }
    }
}
