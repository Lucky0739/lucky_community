package com.lucky.comment.entity;

import lombok.Data;

@Data
public class PostCommentSummary {

    private Long id;
    private Long barId;
    private String barName;
    private String title;
    private String contentPreview;
    private Integer deleted;
}
