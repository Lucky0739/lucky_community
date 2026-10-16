package com.lucky.interaction.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Follow {
    /**
     * 关注id
     */
    private Long id;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 被关注贴吧 id
     */
    private Long barId;
    /**
     * 关注时间
     */
    private LocalDateTime createTime;
}
