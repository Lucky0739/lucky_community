package com.lucky.interaction.service.impl;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.constant.FollowConstant;
import com.lucky.interaction.mapper.BarReadMapper;
import com.lucky.interaction.mapper.FollowMapper;
import com.lucky.interaction.service.FollowFeedService;
import com.lucky.interaction.vo.FollowFeedItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowFeedServiceImpl implements FollowFeedService {

    private final StringRedisTemplate stringRedisTemplate;
    private final FollowMapper followMapper;
    private final BarReadMapper barReadMapper;

    @Override
    public void onPostPublished(Long barId, Long postId, long createTimeMillis) {
        if (barId == null || postId == null) {
            return;
        }
        String member = member(barId, postId);
        String outbox = FollowConstant.barOutboxKey(barId);
        stringRedisTemplate.opsForZSet().add(outbox, member, createTimeMillis);
        trim(outbox, FollowConstant.BAR_OUTBOX_SIZE);

        Integer fans = barReadMapper.selectFollowCount(barId);
        int followCount = fans == null ? 0 : fans;
        if (followCount >= FollowConstant.PULL_FANS_THRESHOLD) {
            log.info("贴吧粉丝数达到拉模式阈值，不推送，barId={}，fans={}", barId, followCount);
            return;
        }
        List<Long> userIds = followMapper.selectUserIdsByBarId(barId);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            String inbox = FollowConstant.followFeedKey(userId);
            stringRedisTemplate.opsForZSet().add(inbox, member, createTimeMillis);
            trim(inbox, FollowConstant.FOLLOW_FEED_SIZE);
        }
        log.info("小吧新帖已推进关注流，barId={}，postId={}，粉丝={}", barId, postId, userIds.size());
    }

    @Override
    public int pull(Long userId) {
        List<Long> barIds = followMapper.selectBarIdsByUserId(userId);
        if (barIds == null || barIds.isEmpty()) {
            return 0;
        }
        String inbox = FollowConstant.followFeedKey(userId);
        int copied = 0;
        for (Long barId : barIds) {
            Integer fans = barReadMapper.selectFollowCount(barId);
            if (fans == null || fans < FollowConstant.PULL_FANS_THRESHOLD) {
                continue;
            }
            Set<ZSetOperations.TypedTuple<String>> posts = stringRedisTemplate.opsForZSet()
                    .rangeWithScores(FollowConstant.barOutboxKey(barId), 0, -1);
            if (posts == null || posts.isEmpty()) {
                continue;
            }
            stringRedisTemplate.opsForZSet().add(inbox, posts);
            copied += posts.size();
        }
        trim(inbox, FollowConstant.FOLLOW_FEED_SIZE);
        log.info("用户拉取大吧关注流完成，userId={}，条数={}", userId, copied);
        return copied;
    }

    @Override
    public PageResult<FollowFeedItemVO> page(Long userId, int pageNo, int pageSize) {
        int safePage = Math.max(pageNo, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        long start = (long) (safePage - 1) * safeSize;
        long end = start + safeSize - 1;
        String inbox = FollowConstant.followFeedKey(userId);
        Long total = stringRedisTemplate.opsForZSet().zCard(inbox);
        Set<ZSetOperations.TypedTuple<String>> range = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(inbox, start, end);
        List<FollowFeedItemVO> records = new ArrayList<>();
        if (range != null) {
            for (ZSetOperations.TypedTuple<String> tuple : range) {
                FollowFeedItemVO item = parse(tuple);
                if (item != null) {
                    records.add(item);
                }
            }
        }
        return new PageResult<>(records, total == null ? 0 : total, safePage, safeSize);
    }

    private FollowFeedItemVO parse(ZSetOperations.TypedTuple<String> tuple) {
        if (tuple.getValue() == null || tuple.getScore() == null) {
            return null;
        }
        String[] parts = tuple.getValue().split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        FollowFeedItemVO item = new FollowFeedItemVO();
        item.setBarId(Long.valueOf(parts[0]));
        item.setPostId(Long.valueOf(parts[1]));
        item.setCreateTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(tuple.getScore().longValue()), ZoneId.systemDefault()));
        return item;
    }

    private String member(Long barId, Long postId) {
        return barId + ":" + postId;
    }

    /** 按时间升序只保留最新的 limit 条 */
    private void trim(String key, int limit) {
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        if (size != null && size > limit) {
            stringRedisTemplate.opsForZSet().removeRange(key, 0, size - limit - 1);
        }
    }
}
