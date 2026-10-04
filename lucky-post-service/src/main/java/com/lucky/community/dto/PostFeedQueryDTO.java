package com.lucky.community.dto;

import com.lucky.common.dto.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子列表的游标分页查询（走查询参数，非请求体）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostFeedQueryDTO extends PageQueryDTO {

    /** 贴吧ID，为空表示不限吧 */
    private Long barId;

    /** 作者user_id，为空表示不限作者 */
    private Long userId;
}
