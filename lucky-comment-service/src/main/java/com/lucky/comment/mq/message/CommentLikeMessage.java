package com.lucky.comment.mq.message;

import lombok.Data;

@Data
public class CommentLikeMessage {

    private Long id;
    private Long userId;
    private Long commentId;
    private Long likeTime;
    private String action;
}
