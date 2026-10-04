package com.lucky.community.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 发给 Python 审核服务的请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModerationRequest {

    @JsonProperty("post_id")
    private Long postId;

    @JsonProperty("bar_id")
    private Long barId;

    @JsonProperty("user_id")
    private Long userId;

    private String title;

    private String content;

    @JsonProperty("image_urls")
    private List<String> imageUrls;
}
