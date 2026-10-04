package com.lucky.community.bar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 建吧请求类
 */
@Data
public class CreateBarDTO {

    /** 吧名，全站唯一（uk_bar_name 兜底） */
    @NotBlank(message = "吧名不能为空")
    @Size(max = 64, message = "吧名不能超过64个字符")
    private String name;

    /** 吧简介 */
    @Size(max = 255, message = "吧简介不能超过255个字符")
    private String description;

    /**
     * 吧头像
     */
    private String avatar;
}
