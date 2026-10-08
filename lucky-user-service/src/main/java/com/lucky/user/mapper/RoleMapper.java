package com.lucky.user.mapper;

import com.lucky.user.entity.Role;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RoleMapper {

    Long selectByRoleId(@Param("roleCode") String roleCode);

    List<Role> selectByIds(@Param("list") List<Long> ids);
}
