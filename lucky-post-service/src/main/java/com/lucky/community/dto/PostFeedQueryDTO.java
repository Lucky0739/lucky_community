package com.lucky.community.dto;

import com.lucky.common.dto.PageQueryDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子列表的游标分页查询（走查询参数，非请求体）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostFeedQueryDTO extends PageQueryDTO {

    /** 贴吧ID。帖子只在吧内列出，不进吧不能翻这个吧的其他帖 */
    @NotNull(message = "请先进入贴吧")
    private Long barId;

    /** 作者user_id，为空表示不限作者 */
    private Long userId;
}
