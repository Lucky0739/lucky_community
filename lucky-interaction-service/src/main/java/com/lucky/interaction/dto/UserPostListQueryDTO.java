package com.lucky.interaction.dto;

import com.lucky.common.dto.OffsetPageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户点赞/收藏帖子列表。走查询参数，非请求体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPostListQueryDTO extends OffsetPageQueryDTO {

    /**
     * 看谁的数据。不传则看当前登录用户自己的
     */
    private Long userId;
}
