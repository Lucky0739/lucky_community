package com.lucky.user.mapper;

import com.lucky.user.entity.BarManager;
import org.apache.ibatis.annotations.Param;

public interface BarManagerMapper {

    int insert(BarManager barManager);

    int revokeByUserId(@Param("userId") Long userId);
}
