package com.lucky.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 未认证/认证失效：映射为 HTTP 401
 * <p>
 * 为什么要和 {@link BusinessException} 分开：业务异常走全局处理器返回的是
 * <b>HTTP 200 + success=false</b>（无 @ResponseStatus），而 {@code AuthInterceptor}
 * 拦截未登录时返回的是裸 401。两者混用会让客户端拿不到一致信号——
 * 前端无法判断「这个 200 到底该不该触发刷新」，也就无法实现「401 → 刷新 → 重放原请求」。
 * <p>
 * 凡是要让客户端去刷新 token 或跳登录页的场景，都抛本异常。
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
