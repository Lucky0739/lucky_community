package com.lucky.interaction.mapper;

import com.lucky.interaction.entity.Thumb;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ThumbMapper {

    long countByPostId(@Param("postId") Long postId);

    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);

    boolean exists(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 批量插入点赞记录，唯一键冲突的跳过。返回**真正插入**的 (userId, postId)，
     * 消费端只对返回的行加作者获赞数，重投不会重复加。
     */
    List<Thumb> batchInsertIgnore(@Param("list") List<Thumb> list);

    /**
     * 批量删除点赞记录，按 (user_id, post_id) 行值 IN。返回**真正删掉**的 (userId, postId)。
     */
    List<Thumb> batchDeleteByUserAndPost(@Param("list") List<Thumb> list);
}
