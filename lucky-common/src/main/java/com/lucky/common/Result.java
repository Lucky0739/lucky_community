package com.lucky.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装（公共模块，各服务共用）
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 是否成功 */
    private Boolean success;
    /** 失败时的错误信息 */
    private String errorMsg;
    /** 业务数据 */
    private T data;
    /** 分页场景下的总条数 */
    private Long total;

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.setSuccess(true);
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<T>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail() {
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setErrorMsg("系统错误，请稍后重试!");
        return result;
    }

    public static <T> Result<T> fail(String errorMsg) {
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setErrorMsg(errorMsg);
        return result;
    }

    public static <T> Result<T> fail(T data) {
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setData(data);
        return result;
    }
}
