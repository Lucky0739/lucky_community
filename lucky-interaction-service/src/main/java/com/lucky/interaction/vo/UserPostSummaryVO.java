package com.lucky.interaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户点赞/收藏列表中的帖子摘要
 */
@Data
public class UserPostSummaryVO {

    private Long postId;
    private Long barId;
    private String barName;
    private Long userId;
    private String nickName;
    private String icon;
    private String title;
    private String contentPreview;
    private String coverImage;
    private Integer imageCount;
    private Long likeCount;
    private Long commentCount;
    private Long favoriteCount;
    private Boolean liked;
    private Boolean favorited;
    private LocalDateTime createTime;
    /** 点赞或收藏时间 */
    private LocalDateTime interactionTime;
}
