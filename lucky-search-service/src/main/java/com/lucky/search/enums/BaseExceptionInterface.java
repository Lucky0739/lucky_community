package com.lucky.search.enums;

/**
 * 业务异常码。搜索服务的 {@link ResponseCodeEnum} 实现它。
 */
public interface BaseExceptionInterface {

    String getErrorCode();

    String getErrorMessage();
}
