package com.lucky.kv.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增或更新帖子正文（Cassandra）。发布帖子前可先拿到 contentUuid。
 */
@Data
public class CreatePostContentRequest {

    @NotNull(message = "帖子 ID 不能为空")
    private Long postId;

    @NotBlank(message = "帖子正文不能为空")
    private String content;
}
