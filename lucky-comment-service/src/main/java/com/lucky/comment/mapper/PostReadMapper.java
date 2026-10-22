package com.lucky.comment.mapper;

import com.lucky.comment.entity.PostCommentSummary;
import com.lucky.comment.entity.PostRef;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostReadMapper {

    PostRef selectById(@Param("id") Long id);

    List<PostCommentSummary> selectSummariesByIds(@Param("ids") List<Long> ids);
}
