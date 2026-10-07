package com.lucky.interaction.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.cache.FollowCache;
import com.lucky.interaction.cache.FolloweeEntry;
import com.lucky.interaction.constant.FollowConstant;
import com.lucky.interaction.dto.FollowListQueryDTO;
import com.lucky.interaction.entity.Follow;
import com.lucky.interaction.enums.FollowLuaResult;
import com.lucky.interaction.enums.UnfollowLuaResult;
import com.lucky.interaction.mapper.FollowMapper;
import com.lucky.interaction.mapper.UserReadMapper;
import com.lucky.interaction.mq.message.FollowMessage;
import com.lucky.interaction.service.FollowCountFlushService;
import com.lucky.interaction.service.FollowService;
import com.lucky.interaction.support.entity.UserBrief;
import com.lucky.interaction.vo.FollowUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final UserReadMapper userReadMapper;
    private final FollowMapper followMapper;
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
        followMessage.setAction(FollowConstant.MQ_TAG);
        try {
            SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                    FollowConstant.MQ_DESTINATION, followMessage, String.valueOf(userId));
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
            followCountFlushService.onFollowEvent(userId, followUserId);
        } catch (RuntimeException e) {
            log.error("关注数刷库窗口更新失败，将等待下次关注或延迟消息，userId={}", userId, e);
        }
    }

    @Override
    public boolean isFollowed(Long userId, Long followUserId) {
        if (followUserId == null) {
            throw new BusinessException("用户不存在");
        }
        if (followUserId.equals(userId)) {
            return false;
        }
        try {
            if (followCache.isFollowed(userId, followUserId)) {
                return true;
            }
            if (followCache.hasFollowZSet(userId)) {
                return false;
            }
        } catch (RuntimeException e) {
            log.error("查询关注关系 Redis 失败，将回源数据库，userId={}, followUserId={}",
                    userId, followUserId, e);
        }
        return followMapper.exists(userId, followUserId);
    }

    @Override
    public void unfollow(Long userId, Long followUserId) {
        if (followUserId == null) {
            throw new BusinessException("用户不存在");
        }
        UnfollowLuaResult unfollowLuaResult;
        try {
            unfollowLuaResult = followCache.unfollow(userId, followUserId);
        } catch (RuntimeException e) {
            log.error("取消关注写入 Redis 失败，userId={}, followUserId={}", userId, followUserId, e);
            throw new BusinessException("取消关注失败，请稍后重试");
        }
        if (unfollowLuaResult != UnfollowLuaResult.SUCCESS) {
            throw new BusinessException(unfollowLuaResult == null
                    ? "取消关注失败，请稍后重试" : unfollowLuaResult.getErrorMsg());
        }

        FollowMessage followMessage = new FollowMessage();
        followMessage.setUserId(userId);
        followMessage.setFollowUserId(followUserId);
        followMessage.setAction(FollowConstant.MQ_UNFOLLOW_TAG);
        try {
            SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                    FollowConstant.MQ_UNFOLLOW_DESTINATION, followMessage, String.valueOf(userId));
            if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("取消关注消息发送状态异常：" +
                        (sendResult == null ? "null" : sendResult.getSendStatus()));
            }
        } catch (RuntimeException e) {
            followCache.restoreAfterUnfollow(userId, followUserId, clock.millis());
            log.error("取消关注消息发送失败，已回滚 Redis，userId={}, followUserId={}", userId, followUserId, e);
            throw new BusinessException("取消关注失败，请稍后重试");
        }

        try {
            followCountFlushService.onFollowEvent(userId, followUserId);
        } catch (RuntimeException e) {
            log.error("关注数刷库窗口更新失败，将等待下次关注或延迟消息，userId={}", userId, e);
        }
    }

    @Override
    public PageResult<FollowUserVO> pageFollowees(Long viewerId, FollowListQueryDTO query) {
        Long ownerId = query.getUserId() == null ? viewerId : query.getUserId();
        if (!userReadMapper.existsById(ownerId)) {
            throw new BusinessException("用户不存在");
        }
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 50);
        int offset = (pageNum - 1) * pageSize;
        String keyword = StringUtils.hasText(query.getKeyword()) ? escapeLike(query.getKeyword().trim()) : null;

        List<FolloweeEntry> entries;
        long total;
        if (keyword != null) {
            // Redis ZSet 只有用户 ID，搜昵称必须联表。关键词组合无穷，不走缓存。
            entries = pageFolloweesFromDb(ownerId, keyword, offset, pageSize);
            total = followMapper.countByUserId(ownerId, keyword);
        } else {
            try {
                List<FolloweeEntry> redisPage = followCache.pageFollowees(ownerId, offset, pageSize);
                if (redisPage != null) {
                    entries = redisPage;
                    total = followCache.readFollowCount(ownerId);
                } else {
                    entries = pageFolloweesFromDb(ownerId, null, offset, pageSize);
                    total = followMapper.countByUserId(ownerId, null);
                }
            } catch (RuntimeException e) {
                log.error("关注列表读 Redis 失败，回源数据库，ownerId={}", ownerId, e);
                entries = pageFolloweesFromDb(ownerId, null, offset, pageSize);
                total = followMapper.countByUserId(ownerId, null);
            }
        }

        if (entries.isEmpty()) {
            return new PageResult<>(List.of(), total, pageNum, pageSize);
        }
        List<Long> followeeIds = new ArrayList<>(entries.size());
        for (FolloweeEntry entry : entries) {
            followeeIds.add(entry.getFollowUserId());
        }
        Map<Long, UserBrief> users = new HashMap<>();
        List<UserBrief> briefs = userReadMapper.selectByIds(followeeIds);
        if (briefs != null) {
            for (UserBrief brief : briefs) {
                users.put(brief.getId(), brief);
            }
        }
        Set<Long> followedIds = resolveFollowed(viewerId, followeeIds);

        List<FollowUserVO> records = new ArrayList<>(entries.size());
        for (FolloweeEntry entry : entries) {
            UserBrief brief = users.get(entry.getFollowUserId());
            if (brief == null) {
                continue;
            }
            FollowUserVO vo = new FollowUserVO();
            vo.setUserId(brief.getId());
            vo.setNickName(brief.getNickName());
            vo.setIcon(brief.getIcon());
            vo.setFollowed(followedIds.contains(brief.getId()));
            vo.setFollowTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(entry.getFollowTimeMillis()), clock.getZone()));
            records.add(vo);
        }
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    private List<FolloweeEntry> pageFolloweesFromDb(Long ownerId, String keyword, int offset, int pageSize) {
        List<Follow> rows = followMapper.selectPageByUserId(ownerId, keyword, offset, pageSize);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<FolloweeEntry> entries = new ArrayList<>(rows.size());
        for (Follow row : rows) {
            long millis = 0L;
            if (row.getCreateTime() != null) {
                millis = row.getCreateTime().atZone(clock.getZone()).toInstant().toEpochMilli();
            }
            entries.add(new FolloweeEntry(row.getFollowUserId(), millis));
        }
        return entries;
    }

    private Set<Long> resolveFollowed(Long viewerId, List<Long> followeeIds) {
        Set<Long> followed = new HashSet<>();
        if (followeeIds == null || followeeIds.isEmpty()) {
            return followed;
        }
        try {
            if (followCache.hasFollowZSet(viewerId)) {
                for (Long followeeId : followeeIds) {
                    if (followCache.isFollowed(viewerId, followeeId)) {
                        followed.add(followeeId);
                    }
                }
                return followed;
            }
        } catch (RuntimeException e) {
            log.error("批量查询关注状态 Redis 失败，回源数据库，viewerId={}", viewerId, e);
        }
        List<Long> ids = followMapper.selectFolloweeIds(viewerId, followeeIds);
        if (ids != null) {
            followed.addAll(ids);
        }
        return followed;
    }

    /** PostgreSQL LIKE / ILIKE 的 % _ \ 转义，避免用户输入被当成通配符 */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
