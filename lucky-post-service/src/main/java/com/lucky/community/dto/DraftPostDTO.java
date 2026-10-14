package com.lucky.community.dto;

import com.lucky.common.constant.PostConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发帖前按图片起草标题和正文。图片直链来自 {@code POST /post/image}。
 */
@Data
public class DraftPostDTO {

    @NotEmpty(message = "请先上传图片")
    @Size(max = PostConstant.MAX_IMAGE_COUNT, message = "最多上传" + PostConstant.MAX_IMAGE_COUNT + "张图片")
    private List<@NotBlank(message = "图片地址不能为空")
            @Size(max = 512, message = "图片地址过长") String> images;
}
