package com.lucky.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Post {

    private Long id;
    private Long barId;
    private Long userId;
    private String title;
    /**
     * 内存载体，**不是数据库列**。完整正文在 Cassandra：
     * 写入时携带原文供组装 VO；从库里读出来的行该字段为 null，
     * 需要正文的地方一律走 {@code PostServiceImpl#loadPostBody}。
     */
    private String content;
    /** 列表摘要，对应 tb_post.content_preview */
    private String contentPreview;
    /** Cassandra 正文主键，对应 tb_post.content_uuid */
    private String contentUuid;
    /** 可见范围：0公开，1仅自己，2仅粉丝 */
    private Integer visibilityScope;
    private String coverImage;
    private Integer imageCount;
    private Integer deleted;
    private Integer eventStatus;
    private Integer embedStatus;
    private Integer analysisStatus;
    private Integer analysisRetryCount;
    private LocalDateTime analysisTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 贴吧列表排序用，不是 tb_post 的列。点赞权重 1，收藏权重 2。 */
    private Long heatScore;
}
