package com.lucky.community.mapper;

import com.lucky.community.entity.PostCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostCountMapper {

    /** 发帖时建一行，点赞、收藏、评论都从 0 开始 */
    int insertZero(@Param("postId") Long postId);

    PostCount selectByPostId(@Param("postId") Long postId);
}
