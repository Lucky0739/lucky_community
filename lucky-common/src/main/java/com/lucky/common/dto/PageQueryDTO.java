package com.lucky.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 游标分页查询基类：游标 + 每页条数
 */
@Data
public class PageQueryDTO {

    /** 每页条数的默认值 */
    public static final int DEFAULT_SIZE = 20;

    /** 每页条数上限 */
    public static final int MAX_SIZE = 50;

    /**
     * 游标：上一页最后一条的 id；首页传 null
     */
    private Long lastId;

    /** 每页条数，上限见 */
    @Min(value = 1, message = "每页条数不能小于1")
    private Integer pageSize = DEFAULT_SIZE;
}
