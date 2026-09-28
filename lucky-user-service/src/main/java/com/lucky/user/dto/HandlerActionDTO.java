package com.lucky.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台管理员对帖子的人工处置
 */
@Data
public class HandlerActionDTO {

    /** 被处置的帖子ID */
    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    /**
     * 处置结论，只能是：未处置、警告、删帖、封禁。
     * 空字符串不算「还没处理」——没处理就不要调这个接口。
     */
    @NotBlank(message = "处置结论不能为空")
    private String handlerAction;
}
