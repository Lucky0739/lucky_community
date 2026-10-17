package com.lucky.comment.cache;

import cn.hutool.core.util.RandomUtil;
import com.lucky.comment.constant.RedisKeyConstants;
import com.lucky.comment.mapper.CommentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 评论数缓存（Redis 计数器）。两个计数的语义**不一样**，别混：
 * <ul>
 *   <li>{@code comment:total:{postId}} —— 只算一级评论（对应 {@code countRootComments}），分页总数用它</li>
 *   <li>{@code comment:reply:total:{rootId}} —— 某个一级评论下的二级回复数（对应 {@code countRepliesByRoot}）</li>
 * </ul>
 * 加减走 Lua：key 不存在就不动（返回 -1 → 回源 {@code count(*)} 重算），避免把未缓存的计数从 0 建起来；
 * 结果下限 0。TTL 只在创建/回填时设一次、**不在写入时刷新**，这样活跃帖的 key 也会周期性过期，
 * 未命中就回源重算，计数才有机会自愈。
 */
@Slf4j
@Component
public class CommentCountCache {

    /** 回填 TTL：1 小时 + 0~4 小时随机，避免同一批 key 同时失效 */
    private static final int BASE_TTL_SECONDS = 60 * 60;
    private static final int JITTER_TTL_SECONDS = 4 * 60 * 60;

    private final StringRedisTemplate stringRedisTemplate;
    private final CommentMapper commentMapper;
    private final DefaultRedisScript<Long> commentCountScript;

    public CommentCountCache(StringRedisTemplate stringRedisTemplate,
                             CommentMapper commentMapper,
                             @Qualifier("commentCountScript") DefaultRedisScript<Long> commentCountScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.commentMapper = commentMapper;
        this.commentCountScript = commentCountScript;
    }

    /** 一级评论数 +delta。delta 为 0 直接跳过 */
    public void incrCommentTotal(Long postId, long delta) {
        if (postId == null || delta == 0) {
            return;
        }
        apply(RedisKeyConstants.buildNoteCommentTotalKey(postId),
                RedisKeyConstants.FIELD_COMMENT_TOTAL, delta, () -> reloadCommentTotal(postId));
    }

    /** 某个一级评论下的二级回复数 +delta */
    public void incrReplyTotal(Long rootCommentId, long delta) {
        if (rootCommentId == null || delta == 0) {
            return;
        }
        apply(RedisKeyConstants.buildReplyCommentTotalKey(rootCommentId),
                RedisKeyConstants.FIELD_REPLY_TOTAL, delta, () -> reloadReplyTotal(rootCommentId));
    }

    /** 一级评论被删，它下面的回复统计整条作废（不是置 0：没人会再读一个已删评论的回复） */
    public void deleteReplyTotal(Long rootCommentId) {
        if (rootCommentId == null) {
            return;
        }
        stringRedisTemplate.delete(RedisKeyConstants.buildReplyCommentTotalKey(rootCommentId));
    }

    /** 读缓存的一级评论数，未命中/值非法返回 null */
    public Long readCommentTotal(Long postId) {
        return read(RedisKeyConstants.buildNoteCommentTotalKey(postId), RedisKeyConstants.FIELD_COMMENT_TOTAL);
    }

    /** 读缓存的二级回复数，未命中/值非法返回 null */
    public Long readReplyTotal(Long rootCommentId) {
        return read(RedisKeyConstants.buildReplyCommentTotalKey(rootCommentId), RedisKeyConstants.FIELD_REPLY_TOTAL);
    }

    /** 把已知的一级评论数回填进缓存（读路径已经 count 过一次，不用再查） */
    public void backfillCommentTotal(Long postId, long count) {
        writeBack(RedisKeyConstants.buildNoteCommentTotalKey(postId), RedisKeyConstants.FIELD_COMMENT_TOTAL, count);
    }

    /** 把已知的二级回复数回填进缓存 */
    public void backfillReplyTotal(Long rootCommentId, long count) {
        writeBack(RedisKeyConstants.buildReplyCommentTotalKey(rootCommentId), RedisKeyConstants.FIELD_REPLY_TOTAL, count);
    }

    /** 回源重算一级评论数并回填，返回重算值 */
    public long reloadCommentTotal(Long postId) {
        long count = commentMapper.countRootComments(postId);
        backfillCommentTotal(postId, count);
        return count;
    }

    /** 回源重算二级回复数并回填，返回重算值 */
    public long reloadReplyTotal(Long rootCommentId) {
        long count = commentMapper.countRepliesByRoot(rootCommentId);
        backfillReplyTotal(rootCommentId, count);
        return count;
    }

    /** 执行加减；脚本返回 -1 表示 key 不存在，此时不建 key，改为回源重算 */
    private long apply(String key, String field, long delta, LongSupplier reload) {
        Long value = stringRedisTemplate.execute(commentCountScript, List.of(key), field, String.valueOf(delta));
        if (value == null || value < 0) {
            return reload.getAsLong();
        }
        return value;
    }

    private Long read(String key, String field) {
        Object cached = stringRedisTemplate.opsForHash().get(key, field);
        if (cached == null) {
            return null;
        }
        try {
            return Long.parseLong(cached.toString());
        } catch (NumberFormatException e) {
            log.warn("评论计数缓存值非法，回源数据库，key={}，value={}", key, cached);
            return null;
        }
    }

    private void writeBack(String key, String field, long count) {
        try {
            stringRedisTemplate.opsForHash().put(key, field, String.valueOf(count));
            stringRedisTemplate.expire(key,
                    BASE_TTL_SECONDS + RandomUtil.randomInt(JITTER_TTL_SECONDS), TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            log.warn("回填评论计数失败，key={}", key, e);
        }
    }
}
