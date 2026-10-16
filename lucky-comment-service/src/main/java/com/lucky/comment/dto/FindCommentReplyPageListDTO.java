package com.lucky.comment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FindCommentReplyPageListDTO {

    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    /** 一级评论 ID */
    @NotNull(message = "根评论ID不能为空")
    private Long rootCommentId;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNo = 1;
}
