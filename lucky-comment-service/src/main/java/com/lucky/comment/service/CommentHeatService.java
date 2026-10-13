package com.lucky.comment.service;

import com.lucky.comment.mq.PublishCommentMessage;

import java.util.List;

/**
 * 新评论写入成功后，给所属贴吧加热度。失败只记日志，不影响评论已经落库。
 */
public interface CommentHeatService {

    void add(List<PublishCommentMessage> freshComments);
}
