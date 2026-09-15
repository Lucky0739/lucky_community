package com.lucky.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类：页码 + 每页条数
 * <p>各业务的查询 DTO 继承它，避免每个列表接口重复声明这两个字段及其校验消息。
 * <p><b>继承方必须加 {@code @EqualsAndHashCode(callSuper = true)}</b>：Lombok 的 {@code @Data}
 * 生成的 equals/hashCode 只认子类自己的字段，不加这个注解会让父类字段被忽略。
 *
 * <h3>走查询参数还是请求体</h3>
 * 两种都适用，由调用方决定：{@code @ModelAttribute} 绑 query string、{@code @RequestBody} 绑 JSON。
 */
@Data
public class PageQueryDTO {

    /**
     * 单页最大条数
     * <p>对外暴露是为了让 MyBatis-Plus 的 {@code PaginationInnerInterceptor#setMaxLimit}
     * 直接引用它——两边各写一个 500 迟早漂移，一个改了另一个没改就会出现
     * 「参数校验放行、分页插件却截断」的静默行为差异
     */
    public static final long MAX_PAGE_SIZE = 500L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    /** 每页条数，上限与分页插件的 maxLimit 对齐（见 {@link #MAX_PAGE_SIZE}） */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = MAX_PAGE_SIZE, message = "分页条数不能超过" + MAX_PAGE_SIZE)
    private Integer pageSize = 10;
}
