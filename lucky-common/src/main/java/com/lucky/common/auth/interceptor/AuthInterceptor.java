package com.lucky.common.auth.interceptor;

import com.lucky.common.auth.annotation.AuthRequired;
import com.lucky.common.auth.TokenRedisService;
import com.lucky.common.auth.JwtUtil;
import com.lucky.common.auth.UserContext;
import com.lucky.common.exception.UnauthorizedException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：只拦标注了 @AuthRequired 的接口
 */
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenRedisService tokenRedisService;

    public AuthInterceptor(JwtUtil jwtUtil, TokenRedisService tokenRedisService) {
        this.jwtUtil = jwtUtil;
        this.tokenRedisService = tokenRedisService;
    }

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        // 只处理 Controller 方法，且只拦标注了 @AuthRequired 的
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean required = handlerMethod.hasMethodAnnotation(AuthRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(AuthRequired.class);
        if (!required) {
            return true;
        }

        // 日志统一带上请求路径，便于排查是哪个接口被拒
        String uri = request.getMethod() + " " + request.getRequestURI();

        // 1. 取 access token
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            log.error("鉴权失败：未携带 access token，uri={}, ip={}", uri, request.getRemoteAddr());
            throw new UnauthorizedException("未登录，请先登录");
        }
        String accessToken = header.substring(BEARER_PREFIX.length());

        // 2. 验签 + 类型 + 时效
        JwtUtil.Payload payload = jwtUtil.parse(accessToken, JwtUtil.TYPE_ACCESS);
        if (payload == null) {
            log.error("鉴权失败：token 验签/类型/时效校验不通过，uri={}, ip={}", uri, request.getRemoteAddr());
            throw new UnauthorizedException("登录已过期，请重新登录");
        }

        // 3. 白名单校验（登出/踢下线后，未过期的 access 也立即失效）
        if (!tokenRedisService.isAccessWhitelisted(payload.userId(), payload.jti())) {
            log.warn("鉴权失败：token 不在白名单(已登出或被踢下线)，uri={}, userId={}, jti={}",
                    uri, payload.userId(), payload.jti());
            throw new UnauthorizedException("登录状态已失效，请重新登录");
        }

        // 4. 撤销校验：用户被删除/封禁后，任何仍在外面的 token 一律拒绝
        if (tokenRedisService.isUserRevoked(payload.userId())) {
            log.warn("鉴权失败：用户已被撤销(删除/封禁)，uri={}, userId={}, jti={}",
                    uri, payload.userId(), payload.jti());
            throw new UnauthorizedException("登录状态已失效，请重新登录");
        }

        // 5. 写入当前登录用户，供业务层使用
        UserContext.setUserId(payload.userId());
        return true;
    }

    @Override
    public void afterCompletion(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                @Nonnull Object handler, Exception ex) {
        // 必须清理，避免线程复用串号
        UserContext.clear();
    }
}
