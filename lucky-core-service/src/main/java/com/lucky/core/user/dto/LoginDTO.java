package com.lucky.core.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 登录请求类
 */
@Data
public class LoginDTO {

    // 手机号
    @NotNull
    private String phone;

    // 用户密码
    @NotNull
    private String password;



}
