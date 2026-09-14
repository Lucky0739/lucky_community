package com.lucky.core.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任命吧务请求类
 */
@Data
public class AppointBarAdminDTO {

    /** 贴吧ID */
    @NotNull(message = "贴吧ID不能为空")
    private Long barId;

    /** 被任命用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
