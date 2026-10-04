package com.lucky.community.mapper;

import com.lucky.community.entity.PostAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostAnalysisMapper {

    int upsert(@Param("a") PostAnalysis analysis);

    int saveHandlerAction(@Param("id") Long id,
                          @Param("postId") Long postId,
                          @Param("handlerAction") String handlerAction);

    PostAnalysis selectByPostId(@Param("postId") Long postId);

    List<PostAnalysis> selectByPostIds(@Param("postIds") List<Long> postIds);

    long countPubliclyVisible(@Param("postId") Long postId);
}
