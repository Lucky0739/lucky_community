package com.lucky.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostImage {

    private Long id;
    private Long postId;
    private String url;
    private String objectName;
    private Integer sortNo;
    private Integer deleted;
    private LocalDateTime createTime;
}
