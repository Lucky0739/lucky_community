package com.lucky.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BarCountMapper {

    int insertZero(@Param("barId") Long barId);
}
