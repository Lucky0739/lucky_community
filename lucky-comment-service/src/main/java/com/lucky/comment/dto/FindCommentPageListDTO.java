package com.lucky.comment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分页查询某篇帖子的评论列表。
 */
@Data
public class FindCommentPageListDTO {

    /** 帖子 ID */
    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    /** 页码，从 1 开始 */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNo = 1;
}
