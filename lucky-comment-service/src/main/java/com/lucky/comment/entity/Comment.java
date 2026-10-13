package com.lucky.comment.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Comment {

    /** 主键，雪花ID */
    private Long id;

    /** 所属帖子ID */
    private Long postId;

    /** 发布者用户ID */
    private Long userId;

    /** 评论内容UUID，对应 Cassandra comment_content.content_id；无正文为空串 */
    private String contentUuid;

    /** 内容是否为空：0不为空，1为空 */
    private Integer isContentEmpty;

    /** 评论附加图片URL；无图为空串 */
    private String imageUrl;

    /** 级别：1一级评论，2二级评论 */
    private Integer level;

    /** 评论被回复次数，仅一级评论需要 */
    private Long replyTotal;

    /** 评论被点赞次数 */
    private Long likeTotal;

    /** 父ID。对帖子的评论存帖子ID；二级评论存一级评论ID */
    private Long parentId;

    /** 回复哪条评论。0表示对帖子的评论；否则存被回复评论ID */
    private Long replyCommentId;

    /** 回复的哪个用户，存用户ID；对帖子的评论为0 */
    private Long replyUserId;

    /** 是否置顶：0不置顶，1置顶 */
    private Integer isTop;

    /** 逻辑删除：0未删除，1已删除 */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
