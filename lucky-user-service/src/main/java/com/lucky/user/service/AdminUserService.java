package com.lucky.user.service;

import com.lucky.user.dto.AdminUserQueryDTO;
import com.lucky.user.dto.HandlerActionDTO;
import com.lucky.user.dto.UpdateProfileDTO;
import com.lucky.user.vo.AdminUserDetailVO;
import com.lucky.user.vo.AdminUserVO;
import com.lucky.user.vo.PageResult;

import java.util.List;

/**
 * 管理员用户管理：删/改/查
 * <p>「增」不在此列，创建走 {@code /user/register}；角色变更走 {@code /user/bar-admin/appoint}。
 * <p>所有方法都要求调用者是平台管理员，校验在 service 层完成（见 {@link PermissionService}）
 */
public interface AdminUserService {

    /**
     * 分页查询用户
     *
     * @param operatorId 操作人(当前登录的平台管理员)
     * @param query      筛选条件，phone 前缀匹配 / nickName 模糊匹配
     * @return 分页结果；调用方取 {@code getRecords()} 作响应体、{@code getTotal()} 作总条数
     */
    PageResult<AdminUserVO> pageUsers(Long operatorId, AdminUserQueryDTO query);

    /**
     * 查用户详情：用户基本信息 + 扩展资料 + 角色
     *
     * @throws com.lucky.common.exception.BusinessException 用户不存在或已被逻辑删除
     */
    AdminUserDetailVO getUserDetail(Long operatorId, Long userId);

    /**
     * 编辑用户资料
     * <p>可改字段与自助编辑完全一致，复用 {@link UserService#updateProfile}；
     * 手机号/密码/角色不在此接口改动
     * <p>目标用户取 {@code dto.getUserId()}；操作者身份只认 token，权限校验在 service 层完成
     *
     * @throws com.lucky.common.exception.BusinessException 用户ID为空 / 用户不存在或已被逻辑删除
     */
    void updateUser(Long operatorId, UpdateProfileDTO dto);

    /**
     * 逻辑删除用户：置 deleted=1 并释放手机号，同时撤销其登录态、清掉角色绑定与吧务身份
     *
     * <p><b>要么全成、要么全不成</b>：整批跑在同一个事务里，任一条不满足条件
     * （删自己 / 已被删除 / 是平台管理员）即抛异常回滚整批，提示语里点明是哪个 userId
     *
     * <p>不幂等：对已删除的用户重复调用会抛「用户不存在」。入参里的重复 id 会被去重——
     * 同一个 id 删两遍，第二遍会撞「已被删除」，把整批白白回滚
     *
     * @param userIds 待删除的用户ID，非空
     * @throws com.lucky.common.exception.BusinessException 入参为空 / 删自己 / 任一用户不存在 / 任一是平台管理员
     */
    void deleteUsers(Long operatorId, List<Long> userIds);

    /**
     * 平台管理员对一条帖子做人工处置，写入 {@code tb_post_analysis.handler_action}。
     * <p>还没有分析行时会补一行，只填处置结论。AI 之后重跑不会盖掉这一列。
     * <ul>
     *   <li>未处置、警告：只记录结论</li>
     *   <li>删帖：记录结论，并逻辑删除帖子</li>
     *   <li>封禁：记录结论，逻辑删除帖子，再按删用户的规则封禁作者</li>
     * </ul>
     *
     * @throws com.lucky.common.exception.BusinessException 非平台管理员、帖子不存在、结论不在允许取值内、封禁对象不能删
     */
    void reviewPost(Long operatorId, HandlerActionDTO dto);
}
