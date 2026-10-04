package com.lucky.community.mapper;

import com.lucky.community.entity.PostImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostImageMapper {

    int insert(PostImage postImage);

    int logicalDeleteByPostId(@Param("postId") Long postId);

    List<PostImage> selectByPostIds(@Param("postIds") List<Long> postIds);
}
