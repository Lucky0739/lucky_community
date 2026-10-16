package com.lucky.comment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteCommentDTO {

    @NotNull(message = "评论ID不能为空")
    private Long commentId;
}
