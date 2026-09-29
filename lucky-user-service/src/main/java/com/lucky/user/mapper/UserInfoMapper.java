package com.lucky.user.mapper;

import com.lucky.user.entity.UserInfo;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

public interface UserInfoMapper {

    int insert(UserInfo userInfo);

    UserInfo selectByUserId(@Param("userId") Long userId);

    int updateProfile(@Param("userId") Long userId,
                      @Param("city") String city,
                      @Param("introduce") String introduce,
                      @Param("gender") Integer gender,
                      @Param("birthday") LocalDate birthday);
}
