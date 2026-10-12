package com.lucky.community.entity;

import lombok.Data;

@Data
public class PostCount {

    private Long postId;
    private Long likeCount;
    private Long favoriteCount;
    private Long commentCount;
}
