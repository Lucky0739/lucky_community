package com.lucky.interaction.mapper;

import com.lucky.interaction.entity.PostSummaryRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PostSummaryReadMapper {

    List<PostSummaryRow> selectApprovedByIds(@Param("ids") List<Long> ids);

    List<Map<String, Object>> selectBarNamesByIds(@Param("ids") List<Long> ids);

    List<Map<String, Object>> selectCountsByPostIds(@Param("postIds") List<Long> postIds);

    List<Map<String, Object>> countCommentsByPostIds(@Param("postIds") List<Long> postIds);
}
