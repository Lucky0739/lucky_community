package com.lucky.core.cookie;

import com.lucky.constant.Constant;
import jakarta.servlet.http.HttpServletResponse;

/**
 * refresh token 的 Cookie 读写工具：统一属性，避免各处硬编码
 * <ul>
 *   <li>HttpOnly：JS 不可读，防 XSS 窃取</li>
 *   <li>SameSite=Lax：阻止跨站请求携带，缓解 CSRF</li>
 *   <li>Path 收窄到续期接口，Secure 由配置控制</li>
 * </ul>
 */
public final class RefreshCookieUtil {

    /** Cookie 作用路径：/user 下，续期与登出接口都能收到 */
    public static final String COOKIE_PATH = "/user";

    private RefreshCookieUtil() {
    }

    /** 写入 refresh token */
    public static void write(HttpServletResponse response, String refreshToken,
                             long maxAgeSeconds, boolean secure) {
        jakarta.servlet.http.Cookie cookie =
                new jakarta.servlet.http.Cookie(Constant.REFRESH_TOKEN_COOKIE, refreshToken);
        applyAttributes(cookie, maxAgeSeconds, secure);
        response.addCookie(cookie);
    }

    /** 清除 refresh token（登出）：MaxAge=0，Path 必须与写入时一致才能命中 */
    public static void clear(HttpServletResponse response, boolean secure) {
        jakarta.servlet.http.Cookie cookie =
                new jakarta.servlet.http.Cookie(Constant.REFRESH_TOKEN_COOKIE, "");
        applyAttributes(cookie, 0, secure);
        response.addCookie(cookie);
    }

    private static void applyAttributes(jakarta.servlet.http.Cookie cookie, long maxAgeSeconds, boolean secure) {
        cookie.setHttpOnly(true);                       // JS 不可读，防 XSS 窃取
        cookie.setSecure(secure);                       // 仅 HTTPS 发送(生产开 true)
        cookie.setAttribute("SameSite", "Lax");         // 阻止跨站请求携带，缓解 CSRF
        cookie.setPath(COOKIE_PATH);                    // 收窄作用域，仅在续期接口生效
        cookie.setMaxAge((int) maxAgeSeconds);          // 与 refresh token 有效期一致
    }
}
