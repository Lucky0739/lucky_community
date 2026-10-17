package com.lucky.comment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论分页列表里的一条评论。
 * 分页外壳用 lucky-common 的 PageResult，本类只描述单条。
 */
@Data
public class FindCommentPageListVO {

    /** 评论 ID */
    private Long commentId;

    /** 所属帖子 ID */
    private Long postId;

    /** 发布者用户 ID */
    private Long userId;

    /** 发布者昵称 */
    private String nickName;

    /** 发布者头像 */
    private String icon;

    /** 级别：1一级评论，2二级评论（回复） */
    private Integer level;

    /** 父ID：对帖子的评论存帖子ID；二级评论存一级评论ID */
    private Long parentId;

    /** 一级评论 ID：对帖子的评论为 0 */
    private Long rootId;

    /** 回复哪条评论：0 表示对帖子的评论，否则是被回复评论 ID */
    private Long replyCommentId;

    /** 被回复的用户 ID；对帖子的评论为 0 */
    private Long replyUserId;

    /** 被回复的用户昵称 */
    private String replyNickName;

    /** 评论正文 */
    private String content;

    /** 评论图片地址；无图为 null */
    private String imageUrl;

    /** 回复数 */
    private Long replyCount;

    /** 点赞数 */
    private Long likeCount;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 是否置顶 */
    private Boolean top;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 首条二级回复，做折叠预览用；没有回复时为 null */
    private FindCommentPageListVO firstReplyComment;
}
