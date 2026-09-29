package com.lucky.user.auth.dto;

/**
 * 刷新结果
 */
public record RefreshResult(String accessToken, String newRefreshToken) {

    public boolean rotated() {
        return newRefreshToken != null;
    }
}
