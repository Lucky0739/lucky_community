package com.lucky.kv.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FindPostContentDTO {

    @NotBlank(message = "帖子 ID 不能为空")
    private String postId;
}
