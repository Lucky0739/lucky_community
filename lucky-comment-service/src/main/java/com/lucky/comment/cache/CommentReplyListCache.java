package com.lucky.comment.cache;

import cn.hutool.core.util.RandomUtil;
import com.lucky.comment.constant.RedisKeyConstants;
import com.lucky.comment.entity.Comment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CommentReplyListCache {

    public static final int MAX_CACHED = 500;

    private static final int MAX_TTL_SECONDS = 5 * 60 * 60;

    private final StringRedisTemplate stringRedisTemplate;

    public boolean exists(Long rootCommentId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(listKey(rootCommentId)));
    }

    public List<Long> rangeReplyIds(Long rootCommentId, long pageSize) {
        Set<String> ids = stringRedisTemplate.opsForZSet().range(listKey(rootCommentId), 0, pageSize - 1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            result.add(Long.valueOf(id));
        }
        return result;
    }

    public void syncIds(Long rootCommentId, Collection<Comment> replies) {
        if (replies == null || replies.isEmpty()) {
            return;
        }
        String key = listKey(rootCommentId);
        for (Comment comment : replies) {
            if (comment.getId() == null || comment.getCreateTime() == null) {
                continue;
            }
            double score = comment.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(comment.getId()), score);
        }
        stringRedisTemplate.opsForZSet().removeRange(key, MAX_CACHED, -1);
        stringRedisTemplate.expire(key, randomTtlSeconds(), TimeUnit.SECONDS);
    }

    public void invalidateList(Long rootCommentId) {
        stringRedisTemplate.delete(listKey(rootCommentId));
    }

    public void invalidateTotal(Long rootCommentId) {
        stringRedisTemplate.delete(RedisKeyConstants.buildReplyCommentTotalKey(rootCommentId));
    }

    private long randomTtlSeconds() {
        return RandomUtil.randomInt(MAX_TTL_SECONDS);
    }

    private String listKey(Long rootCommentId) {
        return RedisKeyConstants.buildReplyCommentListKey(rootCommentId);
    }
}
