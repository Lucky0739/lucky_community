package com.lucky.interaction.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserPostInteraction {

    private Long postId;
    private LocalDateTime interactionTime;
}
