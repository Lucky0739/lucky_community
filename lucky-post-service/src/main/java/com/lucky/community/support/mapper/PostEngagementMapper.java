package com.lucky.community.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostEngagementMapper {

    List<Map<String, Object>> countLikesByPostIds(@Param("postIds") List<Long> postIds);

    List<Long> listLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    List<Map<String, Object>> countFavoritesByPostIds(@Param("postIds") List<Long> postIds);

    List<Long> listFavoritedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    List<Map<String, Object>> countCommentsByPostIds(@Param("postIds") List<Long> postIds);
}
