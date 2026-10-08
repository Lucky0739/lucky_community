package com.lucky.interaction.mapper;

import com.lucky.interaction.support.entity.UserBrief;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserReadMapper {

    boolean existsById(@Param("id") Long id);

    List<UserBrief> selectByIds(@Param("ids") List<Long> ids);
}
