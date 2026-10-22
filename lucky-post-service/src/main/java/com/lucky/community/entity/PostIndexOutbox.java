package com.lucky.community.entity;

import lombok.Data;

/**
 * 帖子索引消息发送失败的兜底记录。与 {@code tb_post_index_outbox} 对应。
 */
@Data
public class PostIndexOutbox {

    /** 主键，雪花ID */
    private Long id;

    /** 消息目的地，格式 topic:tag */
    private String topic;

    /** 消息 JSON */
    private String payload;
}
