package com.lucky.comment.cache;

import com.lucky.comment.constant.RedisKeyConstants;
import com.lucky.comment.entity.Comment;
import com.lucky.comment.enums.CommentLevelEnum;
import com.lucky.comment.mq.message.PublishCommentMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 评论落库 / 删除后的缓存维护（结构类：详情、列表 ZSet、回复列表 ZSet）。
 * 计数不在这里，见 {@link CommentCountCache}。
 */
@Component
@RequiredArgsConstructor
public class CommentCacheCoordinator {

    private final StringRedisTemplate stringRedisTemplate;
    private final CommentListCache commentListCache;
    private final CommentReplyListCache commentReplyListCache;
    private final CommentCountCache commentCountCache;

    public void onCommentsPersisted(Iterable<PublishCommentMessage> fresh) {
        for (PublishCommentMessage message : fresh) {
            if (CommentLevelEnum.ONE.getCode().equals(message.getLevel())) {
                // 只有一级评论动 comment:total；二级回复动的是它父评论的回复计数
                commentCountCache.incrCommentTotal(message.getPostId(), 1);
                stringRedisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(message.getId()));
            } else if (CommentLevelEnum.TWO.getCode().equals(message.getLevel()) && message.getParentId() != null) {
                commentCountCache.incrReplyTotal(message.getParentId(), 1);
                commentReplyListCache.invalidateList(message.getParentId());
                // 父评论的 VO 里带着回复数，详情一起失效让它重建
                stringRedisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(message.getParentId()));
            }
        }
    }

    /** 删一条二级评论后的缓存维护。一级评论被级联删除走 {@link #purgeCascadedRoot(Comment)} */
    public void onCommentDeleted(Comment comment) {
        if (comment == null) {
            return;
        }
        stringRedisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(comment.getId()));
        if (CommentLevelEnum.TWO.getCode().equals(comment.getLevel()) && comment.getParentId() != null) {
            commentReplyListCache.invalidateList(comment.getParentId());
            stringRedisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(comment.getParentId()));
        }
    }

    /**
     * 一级评论被级联删除：它自己的详情、列表项、整份回复列表一起作废。
     * comment:total / comment:reply:total 两个计数由调用方负责（要按真正删掉的行数减）。
     */
    public void purgeCascadedRoot(Comment root) {
        if (root == null) {
            return;
        }
        stringRedisTemplate.delete(RedisKeyConstants.buildCommentDetailKey(root.getId()));
        commentReplyListCache.invalidateList(root.getId());
        commentListCache.remove(root.getPostId(), root.getId());
    }

    /**
     * 批量作废评论详情。必须做：读回复列表时命中的详情是**不查库**直接返回的，
     * 已删回复的详情留着就会被继续读出来。
     */
    public void evictDetails(Collection<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return;
        }
        List<String> keys = commentIds.stream().map(RedisKeyConstants::buildCommentDetailKey).toList();
        stringRedisTemplate.delete(keys);
    }

    public void appendToListCaches(Comment comment) {
        if (comment == null || comment.getId() == null || comment.getCreateTime() == null) {
            return;
        }
        if (CommentLevelEnum.ONE.getCode().equals(comment.getLevel())) {
            commentListCache.syncIds(comment.getPostId(), List.of(comment));
        } else if (CommentLevelEnum.TWO.getCode().equals(comment.getLevel()) && comment.getParentId() != null) {
            commentReplyListCache.syncIds(comment.getParentId(), List.of(comment));
        }
    }
}
