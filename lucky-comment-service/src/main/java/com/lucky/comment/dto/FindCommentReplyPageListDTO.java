package com.lucky.comment.dto;

import com.lucky.common.dto.PageQueryDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FindCommentReplyPageListDTO extends PageQueryDTO {

    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    /** 一级评论 ID */
    @NotNull(message = "根评论ID不能为空")
    private Long rootCommentId;
}
