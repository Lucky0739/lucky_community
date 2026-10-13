package com.lucky.community.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子详情
 */
@Data
public class PostDetailVO {

    /** 帖子ID */
    private Long postId;

    /** 所属贴吧ID */
    private Long barId;

    /** 作者user_id */
    private Long userId;

    /** 作者昵称 */
    private String nickName;

    /** 作者头像 */
    private String icon;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 图片URL列表，按 sort_no 升序 */
    private List<String> images;

    /** 点赞数 */
    private Long likeCount;

    /** 评论数（含回复） */
    private Long commentCount;

    /** 收藏数 */
    private Long favoriteCount;

    /** 当前用户是否已赞；未登录为 false */
    private Boolean liked;

    /** 当前用户是否已收藏；未登录为 false */
    private Boolean favorited;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
