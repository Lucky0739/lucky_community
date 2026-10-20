package com.lucky.interaction.mapper;

import org.apache.ibatis.annotations.Param;

public interface BarCountMapper {

    int insertZero(@Param("barId") Long barId);

    int increaseFanCount(@Param("barId") Long barId);

    int decreaseFanCount(@Param("barId") Long barId);

    Integer selectFanCount(@Param("barId") Long barId);
}
