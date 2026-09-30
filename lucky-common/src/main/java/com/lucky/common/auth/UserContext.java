package com.lucky.common.auth;

/**
 * 当前登录用户上下文（基于 ThreadLocal，由鉴权拦截器写入/清理）
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 请求结束务必调用，避免线程复用导致串号 */
    public static void clear() {
        USER_ID.remove();
    }
}
