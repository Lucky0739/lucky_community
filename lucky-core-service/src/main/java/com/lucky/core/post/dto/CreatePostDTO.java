package com.lucky.core.post.dto;

import com.lucky.common.constant.PostConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发帖请求类（JSON 请求体）
 */
@Data
public class CreatePostDTO {

    /** 发到哪个吧 */
    @NotNull(message = "贴吧ID不能为空")
    private Long barId;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题不能超过64个字符")
    private String title;

    /** 正文 */
    @NotBlank(message = "正文不能为空")
    @Size(max = 5000, message = "正文不能超过5000个字符")
    private String content;

    /**
     * 图片直链列表，来自 {@code POST /post/image} 的返回；纯文字帖可不传
     */
    @Size(max = PostConstant.MAX_IMAGE_COUNT,
            message = "最多上传" + PostConstant.MAX_IMAGE_COUNT + "张图片")
    private List<@NotBlank(message = "图片地址不能为空")
     @Size(max = 512, message = "图片地址过长") String> images;
}
