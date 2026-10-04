package com.lucky.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Post {

    private Long id;
    private Long barId;
    private Long userId;
    private String title;
    private String content;
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
