package com.lucky.comment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发评论。replyCommentId 为空或 0 表示直接评这篇帖子，否则是在回复某条评论。
 */
@Data
public class PublishCommentDTO {

    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    /** 要回复的评论 ID。不传就是一级评论 */
    private Long replyCommentId;

    @Size(max = 500, message = "评论不能超过500字")
    private String content;

    @Size(max = 512, message = "图片地址过长")
    private String imageUrl;
}
