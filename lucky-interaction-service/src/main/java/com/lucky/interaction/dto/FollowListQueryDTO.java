package com.lucky.interaction.dto;

import com.lucky.common.dto.OffsetPageQueryDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 关注列表查询。走查询参数，非请求体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FollowListQueryDTO extends OffsetPageQueryDTO {

    /**
     * 看谁的关注列表。不传则看当前登录用户自己的
     */
    private Long userId;

    /**
     * 按被关注用户昵称模糊搜索。不传则返回全部关注
     */
    @Size(max = 32, message = "关键词不能超过32个字符")
    private String keyword;
}
