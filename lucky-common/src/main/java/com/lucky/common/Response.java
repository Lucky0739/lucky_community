package com.lucky.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应类（公共模块，各服务共用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> {

    /** 成功码 */
    public static final int CODE_SUCCESS = 200;
    /** 默认失败码 */
    public static final int CODE_ERROR = 500;

    /** 业务码：200 成功，其它失败 */
    private Integer code;
    /** 提示信息 */
    private String msg;
    /** 业务数据 */
    private T data;

    public static <T> Response<T> success() {
        return new Response<T>(CODE_SUCCESS, "success", null);
    }

    public static <T> Response<T> success(T data) {
        return new Response<T>(CODE_SUCCESS, "success", data);
    }

    public static <T> Response<T> fail(String msg) {
        return new Response<T>(CODE_ERROR, msg, null);
    }

    public static <T> Response<T> fail(int code, String msg) {
        return new Response<T>(code, msg, null);
    }
}
