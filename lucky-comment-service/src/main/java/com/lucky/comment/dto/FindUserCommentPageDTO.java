package com.lucky.comment.dto;

import com.lucky.common.dto.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户回贴/评论动态游标分页。userId 不传则查当前登录用户（由 Controller 注入）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FindUserCommentPageDTO extends PageQueryDTO {

    /** 看谁发的评论；不传表示当前登录用户 */
    private Long userId;
}
