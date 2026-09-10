package com.lucky.core.interceptor;

import com.lucky.core.annotation.AuthRequired;
import com.lucky.core.service.TokenRedisService;
import com.lucky.core.utils.JwtUtil;
import com.lucky.core.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：只拦标注了 @AuthRequired 的接口
 * 流程：取 Authorization: Bearer <access> → 验签 → 查黑名单 → 写入 UserContext
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenRedisService tokenRedisService;

    public AuthInterceptor(JwtUtil jwtUtil, TokenRedisService tokenRedisService) {
        this.jwtUtil = jwtUtil;
        this.tokenRedisService = tokenRedisService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只处理 Controller 方法，且只拦标注了 @AuthRequired 的
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean required = handlerMethod.hasMethodAnnotation(AuthRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(AuthRequired.class);
        if (!required) {
            return true;
        }

        // 1. 取 access token
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        String accessToken = header.substring(BEARER_PREFIX.length());

        // 2. 验签 + 类型 + 时效
        JwtUtil.Payload payload = jwtUtil.parse(accessToken, JwtUtil.TYPE_ACCESS);
        if (payload == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 3. 黑名单校验（登出/踢下线后，未过期的 access 也立即失效）
        if (tokenRedisService.isAccessBlacklisted(payload.jti())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 4. 写入当前登录用户，供业务层使用
        UserContext.setUserId(payload.userId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理，避免线程复用串号
        UserContext.clear();
    }
}
