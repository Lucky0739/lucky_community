package com.lucky.user.mapper;

import com.lucky.user.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    boolean existsByPhone(@Param("phone") String phone);

    User selectByPhone(@Param("phone") String phone);

    User selectById(@Param("id") Long id);

    int insert(User user);

    int updateProfile(@Param("id") Long id,
                      @Param("nickName") String nickName,
                      @Param("icon") String icon);

    long countByFilter(@Param("phone") String phone, @Param("nickName") String nickName);

    List<User> selectPageByFilter(@Param("phone") String phone,
                                  @Param("nickName") String nickName,
                                  @Param("offset") long offset,
                                  @Param("limit") long limit);

    List<User> selectByIds(@Param("list") List<Long> ids);

    int logicalDeleteAndReleasePhone(@Param("id") Long id);
}
