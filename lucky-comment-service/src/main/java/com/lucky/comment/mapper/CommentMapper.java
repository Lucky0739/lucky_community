package com.lucky.comment.mapper;

import com.lucky.comment.entity.Comment;
import com.lucky.comment.mq.message.PublishCommentMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentMapper {

    Comment selectById(@Param("id") Long id);

    /**
     * 批量插入评论。主键冲突的行跳过，不报错，返回真正插入的行数。
     */
    int batchInsert(@Param("comments") List<Comment> comments);

    /**
     * 按主键插入。已经存在的 ID 跳过，返回这次真正插进去的评论 ID。
     */
    List<Long> insertIgnoreReturningIds(@Param("list") List<PublishCommentMessage> list);

    int increaseReplyTotal(@Param("id") Long id, @Param("delta") int delta);

    long countRootComments(@Param("postId") Long postId);

    List<Comment> selectPageList(@Param("postId") Long postId,
                                 @Param("offset") long offset,
                                 @Param("limit") int limit);

    List<Comment> selectByIds(@Param("ids") List<Long> ids);

    List<Comment> selectTwoLevelCommentByIds(@Param("parentIds") List<Long> parentIds);

    long countRepliesByRoot(@Param("rootCommentId") Long rootCommentId);

    List<Comment> selectReplyPageList(@Param("rootCommentId") Long rootCommentId,
                                      @Param("offset") long offset,
                                      @Param("limit") int limit);

    int softDeleteById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 级联删除用：按 keyset 分页（id &gt; lastId）取某个一级评论下所有未删的二级回复。
     * 只带拼 Cassandra 正文 key 需要的列。用 keyset 而不是 OFFSET，因为边翻旗边翻页 OFFSET 会漏行。
     */
    List<Comment> selectRepliesForCascade(@Param("rootId") Long rootId,
                                          @Param("lastId") long lastId,
                                          @Param("limit") int limit);

    /** 批量软删，返回这次真正翻旗的评论ID（已删的不重复返回，保证计数幂等） */
    List<Long> softDeleteByIds(@Param("ids") List<Long> ids);

    int updateLikeTotal(@Param("commentId") Long commentId, @Param("likeTotal") long likeTotal);
}
