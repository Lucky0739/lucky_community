package com.lucky.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Post {

    private Long id;
    private Long barId;
    private Long userId;
    private String title;
    /** 列表摘要。完整正文在 Cassandra */
    private String contentPreview;
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
}
