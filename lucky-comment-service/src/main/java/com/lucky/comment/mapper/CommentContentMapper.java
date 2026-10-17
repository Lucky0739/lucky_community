package com.lucky.comment.mapper;

import com.lucky.comment.mq.message.PublishCommentMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentContentMapper {

    int insertIgnoreBatch(@Param("list") List<PublishCommentMessage> list);
}
