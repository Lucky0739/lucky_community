package com.lucky.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;

    private long total;

    private int pageNum;

    private int pageSize;

    /** 游标分页：下一页请求传入的 lastId；null 表示没有更多 */
    private Long nextCursor;

    public PageResult(List<T> records, long total) {
        this.records = records;
        this.total = total;
    }

    /**
     * 显式声明四参构造：{@code nextCursor} 加入后，{@code @AllArgsConstructor}
     * 变成五参，原有 {@code new PageResult<>(records, total, pageNum, pageSize)}
     * 的调用会编译失败。这里保留旧签名，nextCursor 由调用方按需 set。
     */
    public PageResult(List<T> records, long total, int pageNum, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
}
