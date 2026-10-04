package com.lucky.community.support.mapper;

import com.lucky.community.support.entity.UserBrief;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserReadMapper {

    UserBrief selectById(@Param("id") Long id);

    List<UserBrief> selectByIds(@Param("ids") List<Long> ids);
}
