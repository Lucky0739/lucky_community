package com.lucky.comment.entity;

import lombok.Data;

@Data
public class PostRef {

    /**
     * 帖子id
     */
    private Long id;

    /**
     * 贴吧id
     */
    private Long barId;

    /**
     * 逻辑删除
     */
    private Integer deleted;
}
