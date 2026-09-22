package com.lucky.core.post.vo;

import lombok.Data;

import java.util.List;

/**
 * 分页结果：本页数据 + 下一页游标
 *
 * <p>服务于<b>游标分页</b>（{@code GET /post/feed}）。与 offset 分页的 {@code IPage} 相比，
 * 这里刻意<b>不返回 total</b>：游标分页服务于无限滚动，它不需要总数；而算总数
 * （{@code SELECT COUNT(*)}）恰恰是 offset 分页里最贵的那条 SQL——省掉它正是游标分页
 * 的主要收益之一。
 *
 * <p>对应的查询参数基类是 {@code PageQueryDTO}（{@code lastId} + {@code size}），
 * 与 offset 那套的 {@code OffsetPageQueryDTO}（{@code pageNum} + {@code pageSize}）
 * 是并列的两套体系，别混用。
 *
 * @param <T> 行类型
 */
@Data
public class PageVO<T> {

    /** 本页数据 */
    private List<T> records;

    /**
     * 下一页游标：把该值原样作为下次请求的 {@code lastId} 传入
     *
     * <p>{@code null} 表示已无更多数据。注意一个边界：当本页<b>恰好装满</b>而数据也正好到底时，
     * 这里仍会返回非 null 的游标，下一次请求才会得到空数组——这是游标分页的常规约定
     * （判断「还有没有下一页」需要多取一条，而那会破坏「缓存与直查返回同一形状」的简洁性）。
     * 客户端以<b>空数组</b>为终止信号即可，不必依赖 null
     */
    private Long nextCursor;
}
