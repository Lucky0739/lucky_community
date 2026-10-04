package com.lucky.community.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommentViewMapper {

    long countRootComments(@Param("postId") Long postId);

    List<Map<String, Object>> selectRootComments(@Param("postId") Long postId, @Param("limit") int limit);

    List<Map<String, Object>> selectContentsByUuids(@Param("uuids") List<String> uuids);
}
