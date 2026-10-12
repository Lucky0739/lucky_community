package com.lucky.community.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {

    Long selectByRoleId(@Param("roleCode") String roleCode);
}
