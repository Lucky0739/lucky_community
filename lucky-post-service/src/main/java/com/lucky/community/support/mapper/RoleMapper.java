package com.lucky.community.support.mapper;

import com.lucky.community.support.entity.RoleRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {

    RoleRef selectByRoleCode(@Param("roleCode") String roleCode);
}
