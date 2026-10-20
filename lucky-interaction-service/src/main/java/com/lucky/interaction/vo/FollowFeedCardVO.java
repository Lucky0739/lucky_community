package com.lucky.interaction.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页关注流卡片：贴吧信息 + 帖子摘要（互动服务读库组装，避免跨服务批量查帖）。
 */
@Data
public class FollowFeedCardVO {

    private Long barId;
    private String barName;
    private String barAvatar;
    private Integer barFollowCount;
    private Integer barPostCount;
    private Boolean followed;

    private Long postId;
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
}
