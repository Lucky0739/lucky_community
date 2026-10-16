package com.lucky.interaction.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostSummaryRow {

    private Long id;
    private Long barId;
    private Long userId;
    private String title;
    private String contentPreview;
    private String coverImage;
    private Integer imageCount;
    private LocalDateTime createTime;
}
