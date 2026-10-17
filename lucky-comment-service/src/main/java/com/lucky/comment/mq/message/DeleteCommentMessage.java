package com.lucky.comment.mq.message;

import lombok.Data;

/**
 * 删除评论消息。只带定位信息：消费者必须回表读权威行
 * （contentUuid + createTime 才能拼出 Cassandra 正文的 key），
 * 把行快照塞进消息反而会在重投时用到过期值。
 * 幂等不靠 messageId，靠消费端 {@code softDeleteById} 的 deleted=0 条件。
 */
@Data
public class DeleteCommentMessage {

    /** 雪花ID，仅用于日志串联 */
    private Long messageId;

    /** 要删除的评论ID */
    private Long commentId;

    /** 发起删除的用户ID，消费端条件删除时再校验一次归属 */
    private Long userId;

    /** 发起删除的时间戳（epoch 毫秒） */
    private Long deleteTime;
}
