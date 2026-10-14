package com.lucky.interaction.service;

public interface ThumbService {

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);
}
