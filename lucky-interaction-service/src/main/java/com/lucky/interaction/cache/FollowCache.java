package com.lucky.interaction.cache;

import com.lucky.interaction.constant.FollowConstant;
import com.lucky.interaction.enums.FollowLuaResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Qualifier("followStatScript")
    private final DefaultRedisScript<Long> followStatScript;

    @Qualifier("followStatDrainScript")
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> followStatDrainScript;

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
     * MQ 发送失败时回滚 ZSet 和关注数，避免 Redis 有关系、库没有
     */
    public void rollback(Long userId, Long followUserId) {
        stringRedisTemplate.execute(
                followRollbackScript,
                List.of(FollowConstant.followUserKey(userId), FollowConstant.followCountKey(userId)),
                String.valueOf(followUserId));
    }

    /**
     * 记下待刷库用户，并判断本窗口是继续等、投递 10 秒延迟消息，还是立即刷库
     */
    public long markFolloweeDirty(Long userId) {
        Long code = stringRedisTemplate.execute(
                followStatScript,
                List.of(
                        FollowConstant.FOLLOWEE_DIRTY_KEY,
                        FollowConstant.FOLLOWEE_EVENT_KEY,
                        FollowConstant.FOLLOWEE_SCHEDULED_KEY),
                String.valueOf(userId),
                String.valueOf(FollowConstant.FLUSH_EVENT_THRESHOLD));
        return code == null ? FollowConstant.STAT_NONE : code;
    }

    /**
     * 取出本窗口脏用户并清空计数。SQL 失败时用 {@link #restoreDirty} 填回去。
     */
    @SuppressWarnings("unchecked")
    public List<String> takeDirtyUserIds() {
        List<Object> users = stringRedisTemplate.execute(
                followStatDrainScript,
                List.of(
                        FollowConstant.FOLLOWEE_DIRTY_KEY,
                        FollowConstant.FOLLOWEE_EVENT_KEY,
                        FollowConstant.FOLLOWEE_SCHEDULED_KEY));
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(users.size());
        for (Object user : users) {
            if (user != null) {
                ids.add(String.valueOf(user));
            }
        }
        return ids;
    }

    public void restoreDirty(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForSet()
                .add(FollowConstant.FOLLOWEE_DIRTY_KEY, userIds.toArray(String[]::new));
    }

    public int readFollowCount(Long userId) {
        String value = stringRedisTemplate.opsForValue().get(FollowConstant.followCountKey(userId));
        if (value == null) {
            Long size = stringRedisTemplate.opsForZSet().zCard(FollowConstant.followUserKey(userId));
            return size == null ? 0 : size.intValue();
        }
        return Integer.parseInt(value);
    }

    public boolean hasDirtyUsers() {
        Long size = stringRedisTemplate.opsForSet().size(FollowConstant.FOLLOWEE_DIRTY_KEY);
        return size != null && size > 0;
    }
}
