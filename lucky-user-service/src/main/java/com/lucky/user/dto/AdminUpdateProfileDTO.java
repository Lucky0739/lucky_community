package com.lucky.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员编辑用户资料请求类（{@code /admin/user/update}）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUpdateProfileDTO extends UpdateProfileDTO {

    /**
     * 目标用户ID：被编辑的那个人，不是当前登录的管理员
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
