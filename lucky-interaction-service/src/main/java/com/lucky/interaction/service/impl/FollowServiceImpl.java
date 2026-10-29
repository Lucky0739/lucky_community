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
import com.lucky.interaction.mapper.BarReadMapper;
import com.lucky.interaction.mapper.FollowMapper;
import com.lucky.interaction.mapper.UserReadMapper;
import com.lucky.interaction.mq.message.FollowMessage;
import com.lucky.interaction.service.FollowService;
import com.lucky.interaction.support.entity.BarBrief;
import com.lucky.common.dto.PageQueryDTO;
import com.lucky.interaction.support.entity.UserBrief;
import com.lucky.interaction.vo.BarFollowerVO;
import com.lucky.interaction.vo.FollowBarSnapshotVO;
import com.lucky.interaction.vo.FollowBarVO;
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
    private final BarReadMapper barReadMapper;
    private final FollowMapper followMapper;
    private final FollowCache followCache;
    private final RocketMQTemplate rocketMQTemplate;
    private final Clock clock;

    @Override
    public void follow(Long userId, Long barId) {
        if (barId == null) {
            throw new BusinessException("传入的贴吧id为空");
        }
        if (!barReadMapper.existsActive(barId)) {
            throw new BusinessException("贴吧不存在");
        }
        long score = clock.millis();
        FollowLuaResult followLuaResult;
        try {
            followLuaResult = followCache.follow(userId, barId, score);
        } catch (RuntimeException e) {
            log.error("关注写入 Redis 失败，userId={}, barId={}", userId, barId, e);
            throw new BusinessException("关注失败，请稍后重试");
        }
        if (followLuaResult != FollowLuaResult.SUCCESS) {
            throw new BusinessException(followLuaResult == null ? "关注失败，请稍后重试" : followLuaResult.getErrorMsg());
        }
        FollowMessage followMessage = new FollowMessage();
        followMessage.setId(IdUtil.getSnowflakeNextId());
        followMessage.setUserId(userId);
        followMessage.setBarId(barId);
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
            followCache.rollback(userId, barId);
            log.error("关注消息发送失败，已回滚 Redis，userId={}, barId={}", userId, barId, e);
            throw new BusinessException("关注失败，请稍后重试");
        }
    }

    @Override
    public boolean isFollowed(Long userId, Long barId) {
        if (barId == null) {
            throw new BusinessException("贴吧不存在");
        }
        try {
            if (followCache.isFollowed(userId, barId)) {
                return true;
            }
            if (followCache.hasFollowZSet(userId)) {
                return false;
            }
        } catch (RuntimeException e) {
            log.error("查询关注关系 Redis 失败，将回源数据库，userId={}, barId={}", userId, barId, e);
        }
        return followMapper.exists(userId, barId);
    }

    @Override
    public void unfollow(Long userId, Long barId) {
        if (barId == null) {
            throw new BusinessException("贴吧不存在");
        }
        UnfollowLuaResult unfollowLuaResult;
        try {
            unfollowLuaResult = followCache.unfollow(userId, barId);
        } catch (RuntimeException e) {
            log.error("取消关注写入 Redis 失败，userId={}, barId={}", userId, barId, e);
            throw new BusinessException("取消关注失败，请稍后重试");
        }
        if (unfollowLuaResult != UnfollowLuaResult.SUCCESS) {
            throw new BusinessException(unfollowLuaResult == null
                    ? "取消关注失败，请稍后重试" : unfollowLuaResult.getErrorMsg());
        }

        FollowMessage followMessage = new FollowMessage();
        followMessage.setUserId(userId);
        followMessage.setBarId(barId);
        followMessage.setAction(FollowConstant.MQ_UNFOLLOW_TAG);
        try {
            SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                    FollowConstant.MQ_UNFOLLOW_DESTINATION, followMessage, String.valueOf(userId));
            if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("取消关注消息发送状态异常：" +
                        (sendResult == null ? "null" : sendResult.getSendStatus()));
            }
        } catch (RuntimeException e) {
            followCache.restoreAfterUnfollow(userId, barId, clock.millis());
            log.error("取消关注消息发送失败，已回滚 Redis，userId={}, barId={}", userId, barId, e);
            throw new BusinessException("取消关注失败，请稍后重试");
        }
    }

    @Override
    public PageResult<FollowBarVO> pageFollowees(Long viewerId, FollowListQueryDTO query) {
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
            // Redis ZSet 只有贴吧 ID，搜吧名必须联表。
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
        Map<Long, BarBrief> bars = new HashMap<>();
        List<BarBrief> briefs = barReadMapper.selectByIds(followeeIds);
        if (briefs != null) {
            for (BarBrief brief : briefs) {
                bars.put(brief.getId(), brief);
            }
        }
        Set<Long> followedIds = resolveFollowed(viewerId, followeeIds);

        List<FollowBarVO> records = new ArrayList<>(entries.size());
        for (FolloweeEntry entry : entries) {
            BarBrief brief = bars.get(entry.getFollowUserId());
            if (brief == null) {
                continue;
            }
            FollowBarVO vo = new FollowBarVO();
            vo.setBarId(brief.getId());
            vo.setName(brief.getName());
            vo.setAvatar(brief.getAvatar());
            vo.setFollowCount(brief.getFollowCount());
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
            entries.add(new FolloweeEntry(row.getBarId(), millis));
        }
        return entries;
    }

    @Override
    public List<FollowBarSnapshotVO> listBarSnapshots(Long viewerId, List<Long> barIds) {
        if (barIds == null || barIds.isEmpty()) {
            return List.of();
        }
        List<Long> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long barId : barIds) {
            if (barId == null || !seen.add(barId)) {
                continue;
            }
            ordered.add(barId);
            if (ordered.size() >= PageQueryDTO.MAX_SIZE) {
                break;
            }
        }
        List<BarBrief> briefs = barReadMapper.selectByIds(ordered);
        if (briefs == null || briefs.isEmpty()) {
            return List.of();
        }
        Map<Long, BarBrief> barById = new HashMap<>();
        for (BarBrief brief : briefs) {
            barById.put(brief.getId(), brief);
        }
        Set<Long> followedIds = resolveFollowed(viewerId, ordered);
        List<FollowBarSnapshotVO> result = new ArrayList<>(ordered.size());
        for (Long barId : ordered) {
            BarBrief brief = barById.get(barId);
            if (brief == null) {
                continue;
            }
            FollowBarSnapshotVO vo = new FollowBarSnapshotVO();
            vo.setBarId(brief.getId());
            vo.setName(brief.getName());
            vo.setAvatar(brief.getAvatar());
            vo.setFollowCount(brief.getFollowCount() == null ? 0 : brief.getFollowCount());
            vo.setPostCount(brief.getPostCount() == null ? 0 : brief.getPostCount());
            vo.setFollowed(followedIds.contains(brief.getId()));
            result.add(vo);
        }
        return result;
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
        List<Long> ids = followMapper.selectFollowedBarIds(viewerId, followeeIds);
        if (ids != null) {
            followed.addAll(ids);
        }
        return followed;
    }

    @Override
    public PageResult<BarFollowerVO> pageBarFollowers(Long barId, int pageNum, int pageSize) {
        if (barId == null) {
            throw new BusinessException("贴吧不存在");
        }
        if (!barReadMapper.existsActive(barId)) {
            throw new BusinessException("贴吧不存在");
        }
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        int offset = (safePageNum - 1) * safePageSize;
        long total = followMapper.countByBarId(barId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePageNum, safePageSize);
        }
        List<Follow> rows = followMapper.selectPageByBarId(barId, offset, safePageSize);
        if (rows.isEmpty()) {
            return new PageResult<>(List.of(), total, safePageNum, safePageSize);
        }
        List<Long> userIds = new ArrayList<>(rows.size());
        for (Follow row : rows) {
            userIds.add(row.getUserId());
        }
        Map<Long, UserBrief> userById = new HashMap<>();
        List<UserBrief> users = userReadMapper.selectByIds(userIds);
        if (users != null) {
            for (UserBrief user : users) {
                userById.put(user.getId(), user);
            }
        }
        List<BarFollowerVO> records = new ArrayList<>(rows.size());
        for (Follow row : rows) {
            BarFollowerVO vo = new BarFollowerVO();
            vo.setUserId(row.getUserId());
            UserBrief user = userById.get(row.getUserId());
            if (user != null) {
                vo.setNickName(user.getNickName());
                vo.setIcon(user.getIcon());
            }
            vo.setFollowTime(row.getCreateTime());
            records.add(vo);
        }
        return new PageResult<>(records, total, safePageNum, safePageSize);
    }

    /** PostgreSQL LIKE / ILIKE 的 % _ \ 转义，避免用户输入被当成通配符 */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
