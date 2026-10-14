package com.lucky.interaction.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PostReadMapper {

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM tb_post WHERE id = #{postId} AND deleted = 0
            )
            """)
    boolean existsActive(@Param("postId") Long postId);

    /** 帖子作者。删掉的帖子也要能查到，取消点赞时还要给作者减获赞 */
    @Select("SELECT user_id FROM tb_post WHERE id = #{postId}")
    Long selectAuthorId(@Param("postId") Long postId);
}
