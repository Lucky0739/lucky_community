package com.lucky.community.dto;

import com.lucky.common.dto.OffsetPageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员查帖子。可以不限吧，用来找还没人工处理的帖子。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPostQueryDTO extends OffsetPageQueryDTO {

    /** 只看这个吧。不传则所有吧 */
    private Long barId;

    /** true 时只看还没有人工结论的帖子 */
    private Boolean pending;
}
