package com.lucky.user.service;

import com.lucky.user.dto.AdminUpdateProfileDTO;
import com.lucky.user.dto.AdminUserQueryDTO;
import com.lucky.user.dto.AppointBarAdminDTO;
import com.lucky.user.dto.HandlerActionDTO;
import com.lucky.user.vo.AdminUserDetailVO;
import com.lucky.user.vo.AdminUserVO;
import com.lucky.common.vo.PageResult;

import java.util.List;

/**
 * 管理员用户管理：删/改/查
 */
public interface AdminUserService {

    /**
     * 分页查询用户
     */
    PageResult<AdminUserVO> pageUsers(Long operatorId, AdminUserQueryDTO query);

    /**
     * 查用户详情：用户基本信息 + 扩展资料 + 角色
     *
     * @throws com.lucky.common.exception.BusinessException 用户不存在或已被逻辑删除
     */
    AdminUserDetailVO getUserDetail(Long operatorId, Long userId);

    /**
     * 编辑用户资料：目标用户由 {@code dto.getUserId()} 指定，不是操作人自己
     *
     * @param operatorId 操作人(当前登录的平台管理员)
     * @throws com.lucky.common.exception.BusinessException 非平台管理员 / 用户不存在或已被逻辑删除
     */
    void updateUser(Long operatorId, AdminUpdateProfileDTO dto);

    /**
     * 任命吧务：写入任命记录并给目标用户绑定 BAR_ADMIN 角色
     *
     * @param operatorId 操作人(当前登录的平台管理员)
     * @param dto        贴吧 ID + 被任命用户 ID
     */
    void appointBarAdmin(Long operatorId, AppointBarAdminDTO dto);

    /**
     * 逻辑删除用户：置 deleted=1 并释放手机号，同时撤销其登录态、清掉角色绑定与吧务身份
     * @throws com.lucky.common.exception.BusinessException 入参为空 / 删自己 / 任一用户不存在 / 任一是平台管理员
     */
    void deleteUsers(Long operatorId, List<Long> userIds);

    /**
     * 平台管理员对一条帖子做人工处置，写入 {@code tb_post_analysis.handler_action}。
     * @throws com.lucky.common.exception.BusinessException 非平台管理员、帖子不存在、结论不在允许取值内、封禁对象不能删
     */
    void reviewPost(Long operatorId, HandlerActionDTO dto);
}
