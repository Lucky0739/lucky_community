package com.lucky.interaction.mq.message;

import lombok.Data;

/**
 * 点赞落库消息。id 在 Lua 成功后生成，消费端原样落 tb_thumb
 */
@Data
public class ThumbMessage {

    private Long id;
    private Long userId;
    private Long postId;
    private Long thumbTime;
    private String action;
}
