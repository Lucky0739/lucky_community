package com.lucky.exception;

import com.lucky.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一转成 Result 返回（依赖它的服务需扫描到 com.lucky.exception）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getMessage());
    }

    /**
     * 认证失效：返回真 401，客户端据此触发「刷新 token → 重放原请求」
     * <p>必须显式声明：下面的 {@code @ExceptionHandler(Exception.class)} 会先命中，
     * 把 {@link UnauthorizedException} 上的 {@code @ResponseStatus} 吞掉，变成 200 + success=false
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(e.getMessage()));
    }

    /**
     * 路径匹配但 HTTP 方法不对（如用 GET 调 POST 接口）：返回 405 而不是 500
     * <p>否则一个用错方法的请求会被兜底分支报成「系统错误」，掩盖真实原因
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail("请求方法不支持，请使用 " + e.getSupportedHttpMethods()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        return Result.fail(fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统错误，请稍后重试!");
    }
}
