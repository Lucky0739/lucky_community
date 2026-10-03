package com.lucky.user.mapper;

import com.lucky.user.entity.UserRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserRoleMapper {

    int insert(UserRole userRole);

    List<UserRole> selectByUserId(@Param("userId") Long userId);

    List<UserRole> selectByUserIds(@Param("list") List<Long> userIds);

    boolean existsByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    boolean existsByUserIdAndRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    int logicalDeleteByUserId(@Param("userId") Long userId);
}
