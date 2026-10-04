package com.lucky.community.mapper;

import com.lucky.community.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostMapper {

    int insert(Post post);

    Post selectById(@Param("id") Long id);

    int updateContent(Post post);

    int resetAnalysisOnEdit(@Param("id") Long id);

    int logicalDeleteById(@Param("id") Long id);

    int updateAnalysisStatus(@Param("id") Long id,
                             @Param("analysisStatus") int analysisStatus,
                             @Param("analysisTime") LocalDateTime analysisTime);

    long countApprovedPage(@Param("barId") Long barId, @Param("userId") Long userId);

    List<Post> selectApprovedPage(@Param("barId") Long barId,
                                  @Param("userId") Long userId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    List<Post> selectApprovedFeed(@Param("barId") Long barId,
                                  @Param("userId") Long userId,
                                  @Param("lastId") Long lastId,
                                  @Param("limit") int limit);

    List<Post> selectApprovedRecent(@Param("limit") int limit);

    List<Long> selectAllIds();

    List<Post> selectByIds(@Param("ids") List<Long> ids);
}
