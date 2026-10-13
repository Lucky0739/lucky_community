package com.lucky.comment.mapper;

import org.apache.ibatis.annotations.Param;

public interface PostCountMapper {

    int addCommentCount(@Param("postId") Long postId, @Param("delta") int delta);
}
