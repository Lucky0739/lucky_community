package com.lucky.user.auth.cookie;

import com.lucky.common.constant.Constant;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * refresh token 的 Cookie 读写工具：统一属性，避免各处硬编码
 */
public final class RefreshCookieUtil {

    public static final String COOKIE_PATH = "/user";

    private RefreshCookieUtil() {
    }

    public static void write(HttpServletResponse response, String refreshToken,
                             long maxAgeSeconds, boolean secure) {
        Cookie cookie = new Cookie(Constant.REFRESH_TOKEN_COOKIE, refreshToken);
        applyAttributes(cookie, maxAgeSeconds, secure);
        response.addCookie(cookie);
    }

    public static void clear(HttpServletResponse response, boolean secure) {
        Cookie cookie = new Cookie(Constant.REFRESH_TOKEN_COOKIE, "");
        applyAttributes(cookie, 0, secure);
        response.addCookie(cookie);
    }

    private static void applyAttributes(Cookie cookie, long maxAgeSeconds, boolean secure) {
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setAttribute("SameSite", "Lax");
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge((int) maxAgeSeconds);
    }
}
