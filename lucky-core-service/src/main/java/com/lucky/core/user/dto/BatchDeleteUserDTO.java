package com.lucky.core.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除用户请求
 */
@Data
public class BatchDeleteUserDTO {

    /**
     * 待删除的用户ID
     */
    @NotEmpty(message = "请选择要删除的用户")
    @Size(max = 100, message = "单次最多删除100个用户")
    private List<@NotNull(message = "用户ID不能为空") Long> userIds;
}
