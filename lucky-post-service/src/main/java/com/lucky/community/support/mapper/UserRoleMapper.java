package com.lucky.community.support.mapper;

import com.lucky.community.support.entity.UserRoleRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper {

    int insert(UserRoleRef userRole);

    boolean existsByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    boolean existsByUserIdAndRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
