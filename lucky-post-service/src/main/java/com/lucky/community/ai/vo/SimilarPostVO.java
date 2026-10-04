package com.lucky.community.ai.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimilarPostVO {

    @JsonProperty("post_id")
    private Long postId;

    private String title;

    private String snippet;

    private Double score;

    @JsonProperty("historical_action")
    private String historicalAction;
}
