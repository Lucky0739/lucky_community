package com.lucky.search.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private Integer pageNo;

    private Integer pageSize;

    private Long total;

    private List<T> records;

    public static <T> PageResponse<T> success(List<T> records, Integer pageNo, long total) {
        List<T> data = records == null ? List.of() : records;
        return new PageResponse<>(pageNo, 10, total, data);
    }
}
