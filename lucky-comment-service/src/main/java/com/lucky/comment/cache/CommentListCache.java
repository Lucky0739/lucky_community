package com.lucky.comment.cache;

import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.comment.constant.RedisKeyConstants;
import com.lucky.comment.entity.Comment;
import com.lucky.comment.vo.FindCommentPageListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 评论列表缓存：两级 Redis 结构。
 *
 * <ul>
 *   <li>{@code comment:list:{postId}} —— ZSet，member = 一级评论 ID，score = 发布时间（epoch 毫秒），
 *       按发布时间升序，只留最早的 {@value #MAX_CACHED} 条（约前 50 页）。</li>
 *   <li>{@code comment:detail:{commentId}} —— 单条评论的 VO JSON，随机过期时间。</li>
 * </ul>
 *
 * 读：ZSet 按 score 升序取一页 ID → MGET 详情 → 缺失的（详情过期）回源数据库补齐。
 * 写：懒加载回填 + 新评论落库时增量 ZADD。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentListCache {

    /** ZSet 里缓存的一级评论条数上限（约前 50 页，每页 10 条） */
    public static final int MAX_CACHED = 500;

    /** 详情缓存的最大过期时间（秒）：随机 0~5 小时 */
    private static final int MAX_DETAIL_TTL_SECONDS = 5 * 60 * 60;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 列表 ZSet 是否已回填 */
    public boolean exists(Long postId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(zsetKey(postId)));
    }

    /** 按发布时间升序取一页评论 ID；返回不足 requested 条说明缓存没覆盖 */
    public List<Long> rangeCommentIds(Long postId, long offset, long pageSize) {
        Set<String> ids = stringRedisTemplate.opsForZSet().rangeByScore(
                zsetKey(postId), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, pageSize);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                result.add(Long.valueOf(id));
            } catch (NumberFormatException e) {
                log.warn("评论列表缓存里的 ID 非法，已跳过，postId={}，id={}", postId, id);
            }
        }
        return result;
    }

    /** MGET 一批评论详情，只返回命中的（未命中的是详情已过期） */
    public Map<Long, FindCommentPageListVO> mgetVOs(List<Long> commentIds) {
        Map<Long, FindCommentPageListVO> result = new HashMap<>();
        if (commentIds.isEmpty()) {
            return result;
        }
        List<String> keys = commentIds.stream()
                .map(RedisKeyConstants::buildCommentDetailKey)
                .toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return result;
        }
        for (int i = 0; i < values.size(); i++) {
            String json = values.get(i);
            if (json == null) {
                continue;
            }
            try {
                result.put(commentIds.get(i), objectMapper.readValue(json, FindCommentPageListVO.class));
            } catch (Exception e) {
                log.warn("评论详情缓存解析失败，已跳过，commentId={}", commentIds.get(i), e);
            }
        }
        return result;
    }

    /**
     * 回填列表 ZSet：id → 发布时间，并按升序只保留最早的 MAX_CACHED 条。
     */
    public void syncIds(Long postId, Collection<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        String key = zsetKey(postId);
        for (Comment comment : comments) {
            if (comment.getId() == null || comment.getCreateTime() == null) {
                continue;
            }
            double score = comment.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(comment.getId()), score);
        }
        // 升序保留最早的 MAX_CACHED 条：把 rank >= MAX_CACHED 的（更新的）删掉
        stringRedisTemplate.opsForZSet().removeRange(key, MAX_CACHED, -1);
        stringRedisTemplate.expire(key, randomDetailTtlSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 评论被删除时把它从列表 ZSet 里摘掉。不摘的话读列表会拿到已删的 id，
     * 这一页就会少一条（详情查不到 → 被过滤掉）。
     */
    public void remove(Long postId, Long commentId) {
        if (postId == null || commentId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(zsetKey(postId), String.valueOf(commentId));
    }

    /** 回填评论详情：逐条 SETEX，随机过期时间，避免同一批 key 同时失效 */
    public void syncVOs(Collection<FindCommentPageListVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        for (FindCommentPageListVO vo : vos) {
            if (vo.getCommentId() == null) {
                continue;
            }
            try {
                String json = objectMapper.writeValueAsString(vo);
                stringRedisTemplate.opsForValue().set(
                        RedisKeyConstants.buildCommentDetailKey(vo.getCommentId()),
                        json,
                        randomDetailTtlSeconds(),
                        TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("回填评论详情缓存失败，commentId={}", vo.getCommentId(), e);
            }
        }
    }

    private long randomDetailTtlSeconds() {
        return RandomUtil.randomInt(MAX_DETAIL_TTL_SECONDS);
    }

    private String zsetKey(Long postId) {
        return RedisKeyConstants.buildNoteCommentListKey(postId);
    }
}
