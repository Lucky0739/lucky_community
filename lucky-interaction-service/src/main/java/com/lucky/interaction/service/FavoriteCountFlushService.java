package com.lucky.interaction.service;

public interface FavoriteCountFlushService {

    void onFavoriteCountChanged(Long postId);

    void flushToDatabase();
}
