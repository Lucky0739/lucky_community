package com.lucky.community.ai.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrievalResultVO {

    @JsonProperty("similar_posts")
    private List<SimilarPostVO> similarPosts;
}
