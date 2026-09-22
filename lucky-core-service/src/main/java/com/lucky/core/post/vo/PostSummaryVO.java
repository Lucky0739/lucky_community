package com.lucky.core.post.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子列表行
 * <p>刻意不返回完整正文：一页 20 条不该扛上百 KB 文本，只给 {@code contentPreview}。
 * <p>作者信息平铺而非嵌套对象，与 {@link AdminUserVO} 保持一致；也绝不返回 User 实体(带 password)
 */
@Data
public class PostSummaryVO {

    /** 帖子ID */
    private Long postId;

    /** 所属贴吧ID */
    private Long barId;

    /** 贴吧名 */
    private String barName;

    /** 作者user_id */
    private Long userId;

    /** 作者昵称；作者已被删除时为 null */
    private String nickName;

    /** 作者头像；作者已被删除时为 null */
    private String icon;

    /** 标题 */
    private String title;

    /** 正文预览(截断) */
    private String contentPreview;

    /** 封面图URL：冗余自 tb_post.cover_image，默认第一张配图；无图时为 null */
    private String coverImage;

    /** 配图总张数：冗余自 tb_post.image_count */
    private Integer imageCount;

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
}
