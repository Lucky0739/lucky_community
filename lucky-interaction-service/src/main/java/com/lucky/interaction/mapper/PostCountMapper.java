package com.lucky.interaction.mapper;

import org.apache.ibatis.annotations.Param;

public interface PostCountMapper {

    /**
     * 写入点赞数。帖子还没有计数行时补一行，收藏数和评论数先写 0。
     */
    int upsertLikeCount(@Param("postId") Long postId, @Param("likeCount") long likeCount);
}
