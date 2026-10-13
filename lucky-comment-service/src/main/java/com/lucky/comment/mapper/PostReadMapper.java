package com.lucky.comment.mapper;

import com.lucky.comment.entity.PostRef;
import org.apache.ibatis.annotations.Param;

public interface PostReadMapper {

    PostRef selectById(@Param("id") Long id);
}
