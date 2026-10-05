package com.lucky.interaction.mapper;

import org.apache.ibatis.annotations.Param;

public interface UserReadMapper {

    boolean existsById(@Param("id") Long id);
}
