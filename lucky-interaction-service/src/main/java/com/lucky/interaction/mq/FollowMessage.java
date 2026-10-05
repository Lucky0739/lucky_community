package com.lucky.interaction.mq;

import lombok.Data;

/**
 * 关注落库消息。id 在写入 Redis 成功后生成，消费端原样落 tb_follow
 */
@Data
public class FollowMessage {

    private Long id;
    private Long userId;
    private Long followUserId;
    private Long followTime;
}
