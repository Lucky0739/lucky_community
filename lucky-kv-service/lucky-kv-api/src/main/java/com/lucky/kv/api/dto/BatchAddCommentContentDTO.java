package com.lucky.kv.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BatchAddCommentContentDTO {

    @NotEmpty(message = "评论内容集合不能为空")
    @Valid
    private List<CommentContentDTO> comments;
}
