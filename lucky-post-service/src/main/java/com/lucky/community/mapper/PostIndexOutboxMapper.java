package com.lucky.community.mapper;

import com.lucky.community.entity.PostIndexOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostIndexOutboxMapper {

    int insert(PostIndexOutbox row);

    List<PostIndexOutbox> selectOldest(@Param("limit") int limit);

    int deleteById(@Param("id") Long id);
}
