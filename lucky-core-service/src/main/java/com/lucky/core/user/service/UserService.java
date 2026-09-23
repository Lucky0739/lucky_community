package com.lucky.core.user.service;

import com.lucky.core.user.dto.AppointBarAdminDTO;
import com.lucky.core.user.dto.LoginDTO;
import com.lucky.core.user.dto.RegisterDTO;
import com.lucky.core.user.dto.UpdateProfileDTO;
import com.lucky.core.user.vo.LoginVO;

public interface UserService {

    /**
     * 注册：手机号查重后落库，密码以 BCrypt 加密存储
     */
    void register(RegisterDTO registerDTO);

    /**
     * 登录：校验手机号+密码，成功返回登录信息（用户身份/角色 + 短 access，长 refresh 由 Controller 写 Cookie）
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 平台管理员任命吧务：写入任命记录并给目标用户绑定 BAR_ADMIN 角色
     * <p>仅平台管理员可调用；同一用户在同一个吧最多一条吧务身份(唯一索引兜底)
     *
     * @param grantUserId 任命人(当前登录的平台管理员)
     * @param dto         贴吧ID + 被任命用户ID
     */
    void appointBarAdmin(Long grantUserId, AppointBarAdminDTO dto);

    /**
     * 编辑个人资料：更新昵称/头像 + 扩展信息(城市/介绍/性别/生日)
     * <p>只更新非空字段，null 表示不修改
     *
     * @param userId 当前登录用户
     */
    void updateProfile(Long userId, UpdateProfileDTO dto);
}
