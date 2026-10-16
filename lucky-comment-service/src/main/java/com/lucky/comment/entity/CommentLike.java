package com.lucky.comment.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentLike {

    private Long id;
    private Long userId;
    private Long commentId;
    private LocalDateTime createTime;
}
