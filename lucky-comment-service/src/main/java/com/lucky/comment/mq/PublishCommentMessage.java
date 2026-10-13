package com.lucky.comment.mq;

import lombok.Data;

/**
 * 发评论消息。id 在发送前生成，重试和补发都用同一个，消费端靠它做幂等。
 */
@Data
public class PublishCommentMessage {

    /** 评论ID，发送前生成，重试和补发都用同一个，消费端靠它做幂等 */
    private Long id;

    /** 所属帖子ID */
    private Long postId;

    /** 所属贴吧ID */
    private Long barId;

    /** 评论作者用户ID */
    private Long userId;

    /** 评论正文UUID，对应 Cassandra comment_content.content_id；无正文为空串 */
    private String contentUuid;

    /** 发布年月，格式 yyyy-MM，正文按月分表用 */
    private String yearMonth;

    /** 评论正文；无正文为空串 */
    private String content;

    /** 评论图片地址；无图片为空串 */
    private String imageUrl;

    /** 级别：1一级评论，2二级评论（回复） */
    private Integer level;

    /** 父ID：对帖子的评论存帖子ID；二级评论存一级评论ID */
    private Long parentId;

    /** 回复哪条评论：0 表示对帖子的评论，否则存被回复评论ID */
    private Long replyCommentId;

    /** 回复的哪个用户：0 表示对帖子的评论，否则存被回复用户ID */
    private Long replyUserId;

    /** 内容是否为空：0 不为空，1 为空（纯图片） */
    private Integer contentEmpty;

    /** 评论创建时间，毫秒时间戳 */
    private Long createTime;
}
