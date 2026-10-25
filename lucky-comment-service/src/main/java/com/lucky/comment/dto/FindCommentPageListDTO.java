package com.lucky.comment.dto;

import com.lucky.common.dto.PageQueryDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 游标分页查询某篇帖子的一级评论列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FindCommentPageListDTO extends PageQueryDTO {

    /** 帖子 ID */
    @NotNull(message = "帖子ID不能为空")
    private Long postId;
}
