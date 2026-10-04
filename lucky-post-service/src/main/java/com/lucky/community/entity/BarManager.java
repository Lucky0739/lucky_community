package com.lucky.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BarManager {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_REVOKED = 0;

    private Long id;
    private Long barId;
    private Long userId;
    private Long grantUserId;
    private LocalDateTime grantTime;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
