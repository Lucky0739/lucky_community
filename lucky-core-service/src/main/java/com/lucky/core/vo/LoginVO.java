package com.lucky.core.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

/**
 * 登录返回体：用户身份(含角色列表) + 短 token
 * <p>refresh token 仅供 Controller 写入 HttpOnly Cookie，不随响应体下发
 */
@Data
public class LoginVO {

    /** 用户主键 */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickName;

    /** 头像 */
    private String icon;

    /** 短 token(访问令牌)：前端存内存，请求头携带 */
    private String accessToken;

    /** 角色列表（一个用户可同时拥有多个身份，如 普通用户 + 商家） */
    private List<RoleVO> roles;

    /** 长 token(刷新令牌)：仅供 Controller 写入 HttpOnly Cookie，不下发到响应体 */
    @JsonIgnore
    private String refreshToken;
}
