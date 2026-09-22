package com.lucky.core.post.dto;

import com.lucky.common.constant.PostConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 改帖请求。不能换吧；图片列表是全量替换，不传或空列表表示去掉全部配图。
 */
@Data
public class UpdatePostDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题不能超过64个字符")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 5000, message = "正文不能超过5000个字符")
    private String content;

    /**
     * 图片直链列表，来自 {@code POST /post/image}；全量替换当前配图。
     * 不传或空列表表示变成纯文字帖。
     */
    @Size(max = PostConstant.MAX_IMAGE_COUNT,
            message = "最多上传" + PostConstant.MAX_IMAGE_COUNT + "张图片")
    private List<@NotBlank(message = "图片地址不能为空")
     @Size(max = 512, message = "图片地址过长") String> images;
}
