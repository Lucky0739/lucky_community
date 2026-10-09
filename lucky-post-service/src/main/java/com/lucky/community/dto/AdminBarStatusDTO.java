package com.lucky.community.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 停用或恢复贴吧。status：1 正常，0 停用。
 */
@Data
public class AdminBarStatusDTO {

    @NotNull(message = "贴吧ID不能为空")
    private Long barId;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
