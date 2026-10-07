package com.lucky.interaction.mq.message;

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
    /** {@link com.lucky.interaction.constant.FollowConstant#MQ_TAG} 或 {@link com.lucky.interaction.constant.FollowConstant#MQ_UNFOLLOW_TAG} */
    private String action;
}
