package com.lucky.comment.entity;

import lombok.Data;

@Data
public class CommentMqOutbox {

    /**
     * 主键，雪花ID
     */
    private Long id;

    /**
     * 消息队列主题，格式 topic:tag
     */
    private String topic;

    /**
     * 消息 JSON。评论主键在第一次生成后保持不变
     */
    private String payload;
}
