package com.lucky.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BarFollowReadMapper {

    List<Long> selectFollowedBarIds(@Param("userId") Long userId, @Param("barIds") List<Long> barIds);
}
