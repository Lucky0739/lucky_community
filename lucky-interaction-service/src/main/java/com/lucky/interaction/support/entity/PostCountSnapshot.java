package com.lucky.interaction.support.entity;

import lombok.Data;

/** 对账后写回的帖子计数。 */
@Data
public class PostCountSnapshot {

    private Long postId;
    private Long likeCount;
    private Long favoriteCount;
    private Long commentCount;
}
