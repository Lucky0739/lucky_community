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
}
