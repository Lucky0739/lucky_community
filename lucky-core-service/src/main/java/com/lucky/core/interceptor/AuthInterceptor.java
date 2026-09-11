package com.lucky.core.interceptor;

import com.lucky.core.annotation.AuthRequired;
import com.lucky.core.service.TokenRedisService;
import com.lucky.core.utils.JwtUtil;
import com.lucky.core.utils.UserContext;
import com.lucky.exception.UnauthorizedException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：只拦标注了 @AuthRequired 的接口
 * 流程：取 Authorization: Bearer <access> → 验签 → 查白名单 → 写入 UserContext
 * <p>未通过时抛 {@link UnauthorizedException}，交由 {@code GlobalExceptionHandler} 统一转成
 * HTTP 401 + 响应体。<b>不</b>直接 {@code setStatus} 返回裸 401：那样客户端只拿得到状态码、
 * 拿不到失败原因，且与刷新路径返回的 401（带响应体）不一致，前端无法用同一套逻辑处理。
 */
@Slf4j
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

        // 4. 写入当前登录用户，供业务层使用
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
