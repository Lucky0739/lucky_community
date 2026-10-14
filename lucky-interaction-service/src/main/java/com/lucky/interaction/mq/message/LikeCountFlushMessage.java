package com.lucky.interaction.mq.message;

import lombok.Data;

@Data
public class LikeCountFlushMessage {

    /** threshold：累计变更触发；timer：10 秒延迟触发 */
    private String reason;

    public static LikeCountFlushMessage threshold() {
        LikeCountFlushMessage message = new LikeCountFlushMessage();
        message.setReason("threshold");
        return message;
    }

    public static LikeCountFlushMessage timer() {
        LikeCountFlushMessage message = new LikeCountFlushMessage();
        message.setReason("timer");
        return message;
    }
}
