package com.lucky.comment.mapper;

import com.lucky.comment.entity.CommentLike;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentLikeMapper {

    List<CommentLike> batchInsertIgnore(@Param("likes") List<CommentLike> likes);

    List<CommentLike> batchDeleteByUserAndComment(@Param("likes") List<CommentLike> likes);

    /** 按评论ID批量删点赞记录，评论被删除时清理，避免留下指向已删评论的孤儿行 */
    int deleteByCommentIds(@Param("commentIds") List<Long> commentIds);
}
