package com.lucky.community.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条评论（详情首屏用）
 */
@Data
public class CommentVO {

    private Long commentId;
    private Long postId;
    private Long userId;
    private String nickName;
    private String icon;
    private Integer level;
    private Long parentId;
    private Long rootId;
    private Long replyCommentId;
    private Long replyUserId;
    private String replyNickName;
    private String content;
    private String imageUrl;
    private Long replyCount;
    private Long likeCount;
    private Boolean liked;
    private Boolean top;
    private LocalDateTime createTime;
}
