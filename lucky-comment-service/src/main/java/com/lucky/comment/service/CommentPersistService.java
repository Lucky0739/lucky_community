package com.lucky.comment.service;

import com.lucky.comment.mq.PublishCommentMessage;

import java.util.List;

public interface CommentPersistService {

    /**
     * 一批写入。返回这次真正新插入的评论，调用方再用它们加热度。
     * 主键冲突的评论不会出现在返回值里，回复数也不会再加。
     */
    List<PublishCommentMessage> saveBatch(List<PublishCommentMessage> messages);
}
