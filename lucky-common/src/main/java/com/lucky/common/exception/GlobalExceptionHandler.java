package com.lucky.common.exception;

import com.lucky.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理：统一转成 Result 返回（依赖它的服务需扫描到 {@code com.lucky}）
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
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(e.getMessage()));
    }

    /**
     * 触发限流：返回真 429
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Result<Void>> handleRateLimit(RateLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Result.fail(e.getMessage()));
    }

    /**
     * 路径匹配但 HTTP 方法不对（如用 GET 调 POST 接口）：返回 405 而不是 500
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail("请求方法不支持，请使用 " + e.getSupportedHttpMethods()));
    }

    /**
     * 请求体校验失败（@RequestBody @Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        return Result.fail(fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage());
    }

    /**
     * 查询参数校验失败（@ModelAttribute @Valid，如分页参数）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        return Result.fail(fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage());
    }

    /**
     * 路径/查询参数类型不匹配（如 /admin/user/abc 里的 userId 转不成 Long）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail("参数类型不正确：" + e.getName());
    }

    /**
     * 上传文件超过大小限制：返回真 413
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制", e);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.fail("上传文件过大，请压缩后重试"));
    }

    /**
     * multipart 请求本身有问题（如 Content-Type 不是 multipart、部分被中途截断）
     */
    @ExceptionHandler(MultipartException.class)
    public Result<Void> handleMultipart(MultipartException e) {
        log.warn("multipart 请求异常", e);
        return Result.fail("上传请求格式不正确：" + e.getMessage());
    }

    /**
     * 缺少必需的 part（如上传接口漏传 {@code file}）
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("缺少必需的请求 part，requestPartName={}", e.getRequestPartName());
        return Result.fail("缺少必需的请求参数：" + e.getRequestPartName());
    }

    /**
     * 请求体无法解析（如 multipart 里的 JSON part 语法错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败", e);
        return Result.fail("请求体格式不正确");
    }

    /**
     * Content-Type 不受支持：返回 415
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.fail("不支持的 Content-Type：" + e.getContentType()));
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统错误，请稍后重试!");
    }
}
