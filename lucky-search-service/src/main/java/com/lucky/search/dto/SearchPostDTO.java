package com.lucky.search.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索帖子的请求参数。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchPostDTO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1;
}
