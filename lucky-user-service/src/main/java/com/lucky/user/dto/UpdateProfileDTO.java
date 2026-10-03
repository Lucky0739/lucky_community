package com.lucky.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑个人资料请求类
 * <p>字段为 null 表示不修改该字段
 */
@Data
public class UpdateProfileDTO {


    /** 昵称 */
    @Size(max = 32, message = "昵称过长")
    private String nickName;

    /** 头像地址(MinIO 返回的 URL) */
    @Size(max = 255, message = "头像地址过长")
    private String icon;

    /** 城市名称 */
    @Size(max = 64, message = "城市名称过长")
    private String city;

    /** 个人介绍 */
    @Size(max = 128, message = "个人介绍过长")
    private String introduce;

    /** 性别：0男，1女 */
    @Min(value = 0, message = "性别取值非法")
    @Max(value = 1, message = "性别取值非法")
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;
}
