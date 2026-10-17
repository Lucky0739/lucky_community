package com.lucky.comment.cache;

import com.lucky.comment.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CommentLikeCache {

    private static final long TTL_SECONDS = 7L * 24 * 3600;

    private final StringRedisTemplate stringRedisTemplate;

    /** @return true 表示新点赞成功 */
    public boolean like(Long userId, Long commentId) {
        Long added = stringRedisTemplate.opsForSet().add(usersKey(commentId), String.valueOf(userId));
        if (added == null || added == 0L) {
            return false;
        }
        stringRedisTemplate.opsForValue().increment(countKey(commentId));
        refreshTtl(commentId);
        return true;
    }

    /** @return true 表示取消成功 */
    public boolean unlike(Long userId, Long commentId) {
        Long removed = stringRedisTemplate.opsForSet().remove(usersKey(commentId), String.valueOf(userId));
        if (removed == null || removed == 0L) {
            return false;
        }
        stringRedisTemplate.opsForValue().decrement(countKey(commentId));
        refreshTtl(commentId);
        return true;
    }

    public boolean isLiked(Long userId, Long commentId) {
        if (userId == null || commentId == null) {
            return false;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(usersKey(commentId), String.valueOf(userId)));
    }

    public Long readCount(Long commentId) {
        String value = stringRedisTemplate.opsForValue().get(countKey(commentId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 评论被删除时清掉它的点赞缓存：计数和用户集合都整条作废 */
    public void evict(Long commentId) {
        if (commentId == null) {
            return;
        }
        stringRedisTemplate.delete(countKey(commentId));
        stringRedisTemplate.delete(usersKey(commentId));
    }

    private void refreshTtl(Long commentId) {
        stringRedisTemplate.expire(usersKey(commentId), Duration.ofSeconds(TTL_SECONDS));
        stringRedisTemplate.expire(countKey(commentId), Duration.ofSeconds(TTL_SECONDS));
    }

    private String countKey(Long commentId) {
        return RedisKeyConstants.buildCommentLikeCountKey(commentId);
    }

    private String usersKey(Long commentId) {
        return RedisKeyConstants.buildCommentLikeUsersKey(commentId);
    }
}
