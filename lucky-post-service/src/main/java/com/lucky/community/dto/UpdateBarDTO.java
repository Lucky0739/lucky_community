package com.lucky.community.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改贴吧。字段为 null 表示不改；至少要改一项。
 */
@Data
public class UpdateBarDTO {

    /** 吧名，全站唯一 */
    @Size(max = 64, message = "吧名不能超过64个字符")
    private String name;

    /** 吧简介 */
    @Size(max = 255, message = "吧简介不能超过255个字符")
    private String description;

    /** 吧头像 URL */
    @Size(max = 255, message = "吧头像地址不能超过255个字符")
    private String avatar;
}
