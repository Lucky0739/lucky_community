package com.lucky.interaction.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.cache.FavoriteCache;
import com.lucky.interaction.cache.PostExistenceCache;
import com.lucky.interaction.constant.FavoriteConstant;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.enums.FavoriteLuaResult;
import com.lucky.interaction.enums.FavoriteUnfavoriteLuaResult;
import com.lucky.interaction.mapper.FavoriteMapper;
import com.lucky.interaction.mq.message.FavoriteMessage;
import com.lucky.interaction.service.FavoriteCountFlushService;
import com.lucky.interaction.service.FavoriteService;
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
public class FavoriteServiceImpl implements FavoriteService {

    private final PostExistenceCache postExistenceCache;
    private final FavoriteCache favoriteCache;
    private final FavoriteMapper favoriteMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final Clock clock;
    private final FavoriteCountFlushService favoriteCountFlushService;
    private final UserInteractionPostQueryService userInteractionPostQueryService;

    @Override
    public void favorite(Long userId, Long postId) {
        if (postId == null) {
            throw new BusinessException("没有传入帖子ID");
        }
        if (!postExistenceCache.exists(postId)) {
            throw new BusinessException("帖子不存在");
        }
        if (!favoriteCache.hasCount(postId)) {
            favoriteCache.preloadCountIfAbsent(postId, favoriteMapper.countByPostId(postId));
        }

        FavoriteLuaResult result = executeFavorite(userId, postId);
        if (result == FavoriteLuaResult.BLOOM_ABSENT) {
            List<Long> favoritedPostIds = favoriteMapper.selectPostIdsByUserId(userId);
            try {
                favoriteCache.initBloom(userId, favoritedPostIds);
            } catch (RuntimeException e) {
                log.error("初始化收藏布隆过滤器失败，userId={}", userId, e);
                throw new BusinessException("收藏失败，请稍后重试");
            }
            result = executeFavorite(userId, postId);
        }
        if (result == FavoriteLuaResult.ALREADY) {
            resendIfUnsynced(userId, postId);
            throw new BusinessException(result.getErrorMsg());
        }
        if (result != FavoriteLuaResult.SUCCESS) {
            throw new BusinessException(result == null ? "收藏失败，请稍后重试" : result.getErrorMsg());
        }
        favoriteCountFlushService.onFavoriteCountChanged(postId);

        long favoriteId = IdUtil.getSnowflakeNextId();
        favoriteCache.markUnsynced(userId, postId, favoriteId);
        try {
            sendPersist(userId, postId, favoriteId, clock.millis());
        } catch (RuntimeException e) {
            log.error("收藏消息发送失败，Redis 中的收藏保留，等待重试补写数据库，userId={}, postId={}",
                    userId, postId, e);
            throw new BusinessException("收藏失败，请稍后重试");
        }
    }

    @Override
    public void unfavorite(Long userId, Long postId) {
        if (postId == null) {
            throw new BusinessException("没有传入帖子ID");
        }
        if (!postExistenceCache.exists(postId)) {
            throw new BusinessException("帖子不存在");
        }
        if (!favoriteCache.hasCount(postId)) {
            favoriteCache.preloadCountIfAbsent(postId, favoriteMapper.countByPostId(postId));
        }

        FavoriteUnfavoriteLuaResult result = prepareAndUnfavorite(userId, postId);
        if (result == FavoriteUnfavoriteLuaResult.NEED_DB) {
            if (!favoriteMapper.exists(userId, postId)) {
                throw new BusinessException(FavoriteUnfavoriteLuaResult.NOT_FAVORITED.getErrorMsg());
            }
            result = executeUnfavorite(userId, postId, true);
            if (result == FavoriteUnfavoriteLuaResult.CONFLICT) {
                result = executeUnfavorite(userId, postId, false);
            }
        }
        if (result == FavoriteUnfavoriteLuaResult.NOT_FAVORITED) {
            throw new BusinessException(result.getErrorMsg());
        }
        if (result != FavoriteUnfavoriteLuaResult.SUCCESS) {
            throw new BusinessException(result == null || result.getErrorMsg() == null
                    ? "取消收藏失败，请稍后重试" : result.getErrorMsg());
        }
        favoriteCountFlushService.onFavoriteCountChanged(postId);

        try {
            sendUnfavorite(userId, postId);
        } catch (RuntimeException e) {
            try {
                favoriteCache.rollbackUnfavorite(userId, postId);
                favoriteCountFlushService.onFavoriteCountChanged(postId);
            } catch (RuntimeException rollbackEx) {
                log.error("取消收藏回滚 Redis 失败，userId={}, postId={}", userId, postId, rollbackEx);
            }
            log.error("取消收藏消息发送失败，已回滚 Redis，userId={}, postId={}", userId, postId, e);
            throw new BusinessException("取消收藏失败，请稍后重试");
        }
        favoriteCache.clearUnsynced(userId, postId);
    }

    @Override
    public PageResult<UserPostSummaryVO> pageFavoritedPosts(Long viewerId, UserPostListQueryDTO query) {
        return userInteractionPostQueryService.pageFavoritedPosts(viewerId, query);
    }

    private FavoriteUnfavoriteLuaResult prepareAndUnfavorite(Long userId, Long postId) {
        FavoriteUnfavoriteLuaResult result = executeUnfavorite(userId, postId, false);
        if (result != FavoriteUnfavoriteLuaResult.BLOOM_ABSENT) {
            return result;
        }
        List<Long> favoritedPostIds = favoriteMapper.selectPostIdsByUserId(userId);
        try {
            favoriteCache.initBloom(userId, favoritedPostIds);
        } catch (RuntimeException e) {
            log.error("初始化收藏布隆过滤器失败，userId={}", userId, e);
            throw new BusinessException("取消收藏失败，请稍后重试");
        }
        return executeUnfavorite(userId, postId, false);
    }

    private void sendUnfavorite(Long userId, Long postId) {
        FavoriteMessage message = new FavoriteMessage();
        message.setUserId(userId);
        message.setPostId(postId);
        message.setAction(FavoriteConstant.MQ_UNFAVORITE_TAG);
        SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                FavoriteConstant.MQ_UNFAVORITE_DESTINATION, message, String.valueOf(userId));
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("取消收藏消息发送状态异常：" +
                    (sendResult == null ? "null" : sendResult.getSendStatus()));
        }
    }

    private FavoriteUnfavoriteLuaResult executeUnfavorite(Long userId, Long postId, boolean dbConfirmed) {
        try {
            return favoriteCache.unfavorite(userId, postId, dbConfirmed);
        } catch (RuntimeException e) {
            log.error("取消收藏写入 Redis 失败，userId={}, postId={}", userId, postId, e);
            throw new BusinessException("取消收藏失败，请稍后重试");
        }
    }

    private void resendIfUnsynced(Long userId, Long postId) {
        Long favoriteId = favoriteCache.unsyncedId(userId, postId);
        if (favoriteId == null) {
            return;
        }
        try {
            sendPersist(userId, postId, favoriteId, clock.millis());
            favoriteCache.clearUnsynced(userId, postId);
        } catch (RuntimeException e) {
            log.error("补发收藏落库消息失败，userId={}, postId={}", userId, postId, e);
        }
    }

    private void sendPersist(Long userId, Long postId, long favoriteId, long favoriteTime) {
        FavoriteMessage message = new FavoriteMessage();
        message.setId(favoriteId);
        message.setUserId(userId);
        message.setPostId(postId);
        message.setFavoriteTime(favoriteTime);
        message.setAction(FavoriteConstant.MQ_TAG);
        SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                FavoriteConstant.MQ_DESTINATION, message, String.valueOf(userId));
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("收藏消息发送状态异常：" +
                    (sendResult == null ? "null" : sendResult.getSendStatus()));
        }
    }

    private FavoriteLuaResult executeFavorite(Long userId, Long postId) {
        try {
            return favoriteCache.favorite(userId, postId);
        } catch (RuntimeException e) {
            log.error("收藏写入 Redis 失败，userId={}, postId={}", userId, postId, e);
            throw new BusinessException("收藏失败，请稍后重试");
        }
    }
}
