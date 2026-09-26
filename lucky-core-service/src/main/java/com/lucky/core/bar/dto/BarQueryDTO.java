package com.lucky.core.bar.dto;

import com.lucky.common.dto.OffsetPageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 吧列表查询请求类（走查询参数，非请求体）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BarQueryDTO extends OffsetPageQueryDTO {

    /** 吧名前缀，为空表示不筛选*/
    private String keyword;
}
