package com.lucky.core.service;

import com.lucky.core.entity.Role;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色查询与鉴权：用户角色的唯一入口
 * <p>为什么不做进拦截器：{@code AuthInterceptor} 的职责刻意收窄为「JWT 验签 + Redis 白名单」，
 * 全链路不碰数据库。若在拦截器里做角色校验，每个已认证请求都要多查两次库
 * （tb_user_role + tb_role），与高并发目标冲突。故沿用 {@code appointBarAdmin}
 * 已确立的先例——在 service 层校验，失败抛 {@link com.lucky.exception.BusinessException}
 */
public interface PermissionService {

    /**
     * 查用户全部角色（tb_user_role 关联 tb_role）
     * <p>一个用户可同时拥有多个角色(如 普通吧友 + 吧务)，故返回列表而非单条
     *
     * @return 角色列表；未绑定任何角色时返回空列表
     */
    List<Role> listRoles(Long userId);

    /**
     * 批量查角色：userId -> 角色列表
     * <p>列表页组装用，一次查询取回多个用户的角色，避免 N+1
     *
     * @return 仅包含有角色绑定的用户；入参为空时返回空 Map
     */
    Map<Long, List<Role>> listRolesByUserIds(Collection<Long> userIds);

    /**
     * 是否拥有平台管理员角色：鉴权的唯一方法
     * <p>只给判断不给断言，是因为调用方的失败语义并不统一：多数场景提示语为「仅平台管理员可访问」，
     * 任命吧务要的是「仅平台管理员可任命吧务」，而删除用户时判断的甚至是「被删目标是不是管理员」
     * 这类业务规则而非操作人鉴权。断言收在调用方，由各自抛 {@link com.lucky.exception.BusinessException}
     * <p>与本接口其余方法不同，它不返回 {@link Role} 列表，只回一个布尔值——实现走单条 SQL，
     * 鉴权路径上不要改成「拿 {@link #listRoles} 再在内存里找」
     */
    boolean isPlatformAdmin(Long userId);

    /**
     * 从一批用户里挑出拥有平台管理员角色的
     * <p>批量鉴权用：一次查询取回所有人的角色再内存比对，避免逐个调 {@link #isPlatformAdmin} 打库。
     * 与它的关系是「同一问题的两种实现」——单点用单条 SQL，整批用批量查询，
     * 两者的判据都取自 {@link com.lucky.enums.RoleType#PLATFORM_ADMIN}，改角色码时两边自动一致
     *
     * @return 其中是平台管理员的 userId；入参为空时返回空集合
     */
    Set<Long> filterPlatformAdmins(Collection<Long> userIds);
}
