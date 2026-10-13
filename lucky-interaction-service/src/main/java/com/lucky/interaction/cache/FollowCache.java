package com.lucky.interaction.cache;

import com.lucky.interaction.constant.FollowConstant;
import com.lucky.interaction.enums.FollowLuaResult;
import com.lucky.interaction.enums.UnfollowLuaResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 关注热路径：ZSet + 关注数都由 Lua 一次写完
 */
@Service
@RequiredArgsConstructor
public class FollowCache {

    private final StringRedisTemplate stringRedisTemplate;

    @Qualifier("followScript")
    private final DefaultRedisScript<Long> followScript;

    @Qualifier("followRollbackScript")
    private final DefaultRedisScript<Long> followRollbackScript;

    @Qualifier("unfollowScript")
    private final DefaultRedisScript<Long> unfollowScript;

    @Qualifier("unfollowRollbackScript")
    private final DefaultRedisScript<Long> unfollowRollbackScript;

    /**
     * 执行关注Lua脚本
     * @param userId
     * @param followUserId
     * @param score
     * @return
     */
    public FollowLuaResult follow(Long userId, Long followUserId, long score) {
        Long code = stringRedisTemplate.execute(
                followScript,
                List.of(FollowConstant.followUserKey(userId), FollowConstant.followCountKey(userId)),
                String.valueOf(userId),
                String.valueOf(followUserId),
                String.valueOf(score),
                String.valueOf(FollowConstant.MAX_FOLLOWEE));
        return FollowLuaResult.fromCode(code);
    }

    /**
     * 是否已关注：ZSet 里有该 member 即为已关注
     */
    public boolean isFollowed(Long userId, Long followUserId) {
        Double score = stringRedisTemplate.opsForZSet()
                .score(FollowConstant.followUserKey(userId), String.valueOf(followUserId));
        return score != null;
    }

    /**
     * 该用户的关注 ZSet 是否已在 Redis。没有 key 时只能回源 tb_follow。
     */
    public boolean hasFollowZSet(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(FollowConstant.followUserKey(userId)));
    }

    public UnfollowLuaResult unfollow(Long userId, Long followUserId) {
        Long code = stringRedisTemplate.execute(
                unfollowScript,
                List.of(FollowConstant.followUserKey(userId), FollowConstant.followCountKey(userId)),
                String.valueOf(followUserId));
        return UnfollowLuaResult.fromCode(code);
    }

    /**
     * 取消关注的 MQ 发送失败时，把 ZSet 关系和关注数加回去
     */
    public void restoreAfterUnfollow(Long userId, Long followUserId, long score) {
        stringRedisTemplate.execute(
                unfollowRollbackScript,
                List.of(FollowConstant.followUserKey(userId), FollowConstant.followCountKey(userId)),
                String.valueOf(followUserId),
                String.valueOf(score));
    }

    /**
     * MQ 发送失败时回滚 ZSet 和关注数，避免 Redis 有关系、库没有
     */
    public void rollback(Long userId, Long followUserId) {
        stringRedisTemplate.execute(
                followRollbackScript,
                List.of(FollowConstant.followUserKey(userId), FollowConstant.followCountKey(userId)),
                String.valueOf(followUserId));
    }

    public int readFollowCount(Long userId) {
        String value = stringRedisTemplate.opsForValue().get(FollowConstant.followCountKey(userId));
        if (value == null) {
            Long size = stringRedisTemplate.opsForZSet().zCard(FollowConstant.followUserKey(userId));
            return size == null ? 0 : size.intValue();
        }
        return Integer.parseInt(value);
    }

    /**
     * 关注列表：按关注时间倒序取一页。ZSet 不存在时返回 null，由调用方回源数据库。
     */
    public List<FolloweeEntry> pageFollowees(Long userId, long offset, int count) {
        if (!hasFollowZSet(userId)) {
            return null;
        }
        if (offset < 0 || count <= 0) {
            return List.of();
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(FollowConstant.followUserKey(userId), offset, offset + count - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<FolloweeEntry> entries = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null) {
                continue;
            }
            try {
                long score = tuple.getScore() == null ? 0L : tuple.getScore().longValue();
                entries.add(new FolloweeEntry(Long.valueOf(tuple.getValue()), score));
            } catch (NumberFormatException e) {
                // 脏 member 跳过，不让整页失败
            }
        }
        return entries;
    }
}
