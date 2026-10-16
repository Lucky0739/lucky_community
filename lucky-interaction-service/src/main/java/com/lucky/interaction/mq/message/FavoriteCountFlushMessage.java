package com.lucky.interaction.mq.message;

import lombok.Data;

@Data
public class FavoriteCountFlushMessage {

    private String reason;

    public static FavoriteCountFlushMessage threshold() {
        FavoriteCountFlushMessage message = new FavoriteCountFlushMessage();
        message.setReason("threshold");
        return message;
    }

    public static FavoriteCountFlushMessage timer() {
        FavoriteCountFlushMessage message = new FavoriteCountFlushMessage();
        message.setReason("timer");
        return message;
    }
}
