package com.lucky.community.cache;

import com.lucky.redis.RedisKeyManage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 贴吧热度榜
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarHeatCache {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 把吧纳入热度榜，初始热度 0。建吧时调用
     */
    public void addBar(Long barId) {
        if (barId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().add(RedisKeyManage.BAR_HOT.key(), member(barId), 0d);
        } catch (Exception e) {
            log.warn("把吧加入热度榜失败，该吧将不出现在热度排序里，barId={}", barId, e);
        }
    }

    /**
     * 调整热度。发帖传 {@code +1}，逻辑删帖传 {@code -1}
     */
    public void increment(Long barId, double delta) {
        if (barId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().incrementScore(RedisKeyManage.BAR_HOT.key(), member(barId), delta);
        } catch (Exception e) {
            // 热度少加一次不影响正确性（只影响排序），不值得让发帖失败
            log.warn("调整吧热度失败，barId={}，delta={}", barId, delta, e);
        }
    }

    /**
     * 按热度倒序取一页吧 ID
     */
    public List<Long> pageBarIds(int offset, int count) {
        if (offset < 0 || count <= 0) {
            return List.of();
        }
        try {
            Set<String> members = stringRedisTemplate.opsForZSet()
                    .reverseRange(RedisKeyManage.BAR_HOT.key(), offset, (long) offset + count - 1);
            if (members == null) {
                return List.of();
            }
            // 必须保序：reverseRange 返回的 LinkedHashSet 就是热度倒序，重排就白排了
            List<Long> ids = new ArrayList<>(members.size());
            for (String member : members) {
                try {
                    ids.add(Long.valueOf(member));
                } catch (NumberFormatException e) {
                    // 榜里混进了非 ID 的成员（有人手工 ZADD 了脏数据），跳过它而不是让整页失败
                    log.warn("热度榜中存在非法的 barId，已跳过，member={}", member);
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("读取吧热度榜失败，将回退 PostgreSQL，offset={}, count={}", offset, count, e);
            return null;
        }
    }

    /**
     * 榜上吧的总数，用作分页 total
     */
    public Long count() {
        try {
            return stringRedisTemplate.opsForZSet().zCard(RedisKeyManage.BAR_HOT.key());
        } catch (Exception e) {
            log.warn("读取吧热度榜总数失败，将回退 PostgreSQL", e);
            return null;
        }
    }

    /** ZSet 的 member 统一用字符串形式的 barId */
    private String member(Long barId) {
        return String.valueOf(barId);
    }
}
