package com.lucky.interaction.mapper;

import com.lucky.interaction.entity.Thumb;
import com.lucky.interaction.entity.UserPostInteraction;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ThumbMapper {

    long countByPostId(@Param("postId") Long postId);

    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);

    boolean exists(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 批量插入点赞记录，唯一键冲突的跳过，重投幂等。 */
    List<Thumb> batchInsertIgnore(@Param("list") List<Thumb> list);

    /** 批量删除点赞记录，按 (user_id, post_id) 行值 IN，重投幂等。 */
    List<Thumb> batchDeleteByUserAndPost(@Param("list") List<Thumb> list);

    long countVisibleByUserId(@Param("userId") Long userId);

    List<UserPostInteraction> selectVisiblePageByUserId(@Param("userId") Long userId,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);

    List<Long> listLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
