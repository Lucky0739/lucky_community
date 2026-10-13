package com.lucky.comment.mapper;

import com.lucky.comment.entity.Comment;
import com.lucky.comment.mq.PublishCommentMessage;
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
}
