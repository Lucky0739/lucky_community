package com.lucky.interaction.mapper;

import com.lucky.interaction.support.entity.BarBrief;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BarReadMapper {

    boolean existsActive(@Param("barId") Long barId);

    Integer selectFollowCount(@Param("barId") Long barId);

    List<BarBrief> selectByIds(@Param("ids") List<Long> ids);
}
