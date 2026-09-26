package com.lucky.common.exception;

/**
 * 业务异常：校验失败等可预期错误，由全局异常处理器转成统一响应
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
