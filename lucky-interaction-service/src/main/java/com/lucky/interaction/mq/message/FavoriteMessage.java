package com.lucky.interaction.mq.message;

import lombok.Data;

@Data
public class FavoriteMessage {

    private Long id;
    private Long userId;
    private Long postId;
    private Long favoriteTime;
    private String action;
}
