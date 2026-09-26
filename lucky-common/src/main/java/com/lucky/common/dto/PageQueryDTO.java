package com.lucky.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 游标分页查询基类：游标 + 每页条数
 */
@Data
public class PageQueryDTO {

    /**
     * 单页最大条数
     */
    public static final int MAX_SIZE = 50;

    /** 每页条数的默认值 */
    public static final int DEFAULT_SIZE = 20;

    /**
     * 游标：上一页最后一条的 id；首页传 null
     */
    private Long lastId;

    /** 每页条数，上限见 {@link #MAX_SIZE} */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = MAX_SIZE, message = "每页条数不能超过" + MAX_SIZE)
    private Integer size = DEFAULT_SIZE;
}
