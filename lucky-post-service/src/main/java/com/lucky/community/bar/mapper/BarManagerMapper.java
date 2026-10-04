package com.lucky.community.bar.mapper;

import com.lucky.community.bar.entity.BarManager;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BarManagerMapper {

    int insert(BarManager barManager);

    long countActiveByBarAndUser(@Param("barId") Long barId,
                                 @Param("userId") Long userId,
                                 @Param("status") int status);
}
