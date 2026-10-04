package com.lucky.community.dto;

import com.lucky.common.dto.OffsetPageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子列表查询请求类（走查询参数，非请求体）
 * <p>分页字段（pageNum / pageSize）继承自 {@link OffsetPageQueryDTO}
 * <p>筛选条件为空表示不参与筛选
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostQueryDTO extends OffsetPageQueryDTO {

    /** 贴吧ID，为空表示不限吧 */
    private Long barId;

    /** 作者user_id，为空表示不限作者(「某人的帖子」) */
    private Long userId;
}
