package com.lucky.user.service;


import com.lucky.user.dto.LoginDTO;
import com.lucky.user.dto.RegisterDTO;
import com.lucky.user.dto.UpdateProfileDTO;
import com.lucky.user.api.vo.UserBriefVO;
import com.lucky.user.vo.LoginVO;

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
     * 自助编辑个人资料：更新昵称/头像 + 扩展信息(城市/介绍/性别/生日)
     *
     * @throws com.lucky.common.exception.UnauthorizedException 无登录上下文（未走鉴权拦截器）
     */
    void updateProfile(UpdateProfileDTO dto);

    /**
     * 指定目标编辑个人资料：改的是 {@code userId} 指向的用户，供管理员路径使用
     *
     * @param userId 目标用户，不是操作人
     */
    void updateProfileForUser(Long userId, UpdateProfileDTO dto);

    /** 帖子详情展示作者。用户不存在时返回 null。 */
    UserBriefVO getBrief(Long userId);
}
