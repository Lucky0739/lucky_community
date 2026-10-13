package com.lucky.comment.service;

import com.lucky.comment.dto.PublishCommentDTO;

public interface CommentPublishService {

    /**
     * 生成评论 ID 后发消息。接口马上返回这个 ID，正文由消费端写入数据库。
     */
    Long publish(PublishCommentDTO publishCommentDTO);
}
