package com.lucky.core.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lucky.core.user.entity.UserRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 判断用户是否绑定了指定角色码的角色
     */
    @Select("SELECT EXISTS(SELECT 1 FROM tb_user_role ur "
            + "JOIN tb_role r ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.role_code = #{roleCode})")
    boolean existsByUserIdAndRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
