package com.lucky.comment.mapper;

import com.lucky.comment.entity.CommentMqOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentMqOutboxMapper {

    int insert(CommentMqOutbox row);

    List<CommentMqOutbox> selectOldest(@Param("limit") int limit);

    int deleteById(@Param("id") Long id);
}
