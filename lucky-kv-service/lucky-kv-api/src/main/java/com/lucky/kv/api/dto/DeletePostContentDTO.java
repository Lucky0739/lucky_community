package com.lucky.kv.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeletePostContentDTO {

    @NotBlank(message = "笔记 ID 不能为空")
    private String uuid;
}
