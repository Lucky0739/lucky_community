package com.lucky.interaction.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Thumb {

    private Long id;
    private Long userId;
    private Long postId;
    private LocalDateTime createTime;
}
