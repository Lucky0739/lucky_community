package com.lucky.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * offset 分页查询基类：页码 + 每页条数
 */
@Data
public class OffsetPageQueryDTO {

    /**
     * 单页最大条数
     */
    public static final long MAX_PAGE_SIZE = 500L;

    /**
     * 最大页码
     */
    public static final long MAX_PAGE_NUM = 1000L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    @Max(value = MAX_PAGE_NUM, message = "页码不能超过" + MAX_PAGE_NUM)
    private Integer pageNum = 1;

    /** 每页条数，上限与分页插件的 maxLimit 对齐 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = MAX_PAGE_SIZE, message = "分页条数不能超过" + MAX_PAGE_SIZE)
    private Integer pageSize = 10;
}
