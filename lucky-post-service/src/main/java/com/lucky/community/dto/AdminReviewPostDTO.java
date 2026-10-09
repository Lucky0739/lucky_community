package com.lucky.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台管理员人工审核帖子。结论只能是：未处置、警告、删帖、封禁。
 */
@Data
public class AdminReviewPostDTO {

    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    @NotBlank(message = "审核结论不能为空")
    private String handlerAction;
}
