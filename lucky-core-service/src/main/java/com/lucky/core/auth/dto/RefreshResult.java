package com.lucky.core.auth.dto;

/**
 * 刷新结果
 *
 * @param accessToken     新的短 token，始终返回
 * @param newRefreshToken 新的长 token；为 {@code null} 表示本次是宽限复用（并发/重复刷新），
 *                        没有新建会话，调用方<b>不要</b>改写 Cookie——否则会把别处刚轮换出的
 *                        新会话又覆盖回去
 */
public record RefreshResult(String accessToken, String newRefreshToken) {

    /** 是否产生了新会话（需要写回 Cookie） */
    public boolean rotated() {
        return newRefreshToken != null;
    }
}
