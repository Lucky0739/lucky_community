package com.lucky.community.dto;

import com.lucky.common.dto.OffsetPageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员查贴吧。status 不传则正常和停用都返回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminBarQueryDTO extends OffsetPageQueryDTO {

    /** 吧名前缀 */
    private String keyword;

    /** 1 正常，0 停用。不传表示不限 */
    private Integer status;
}
