package com.lucky.community.dto;

import com.lucky.common.dto.PageQueryDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 吧内帖子瀑布流。只按游标往下翻，不提供页码跳转。
 * <p>{@code lastId} 为空是第一页；之后把上一页的 {@code nextCursor} 原样传回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostQueryDTO extends PageQueryDTO {

    /** 贴吧ID。帖子只在吧内列出，不进吧不能翻这个吧的其他帖 */
    @NotNull(message = "请先进入贴吧")
    private Long barId;

    /** 作者user_id，为空表示不限作者(「某人的帖子」) */
    private Long userId;
}
