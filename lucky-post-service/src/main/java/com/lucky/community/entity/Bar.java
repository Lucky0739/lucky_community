package com.lucky.community.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Bar {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DISABLED = 0;

    private Long id;
    private String name;
    private String description;
    private String avatar;
    private Integer followCount;
    private Integer postCount;
    private Long ownerUserId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
