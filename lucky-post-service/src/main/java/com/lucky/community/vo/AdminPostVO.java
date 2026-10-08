package com.lucky.community.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员看到的帖子行。带吧名和人工结论，方便在后台跨吧审帖。
 */
@Data
public class AdminPostVO {

    private Long postId;
    private Long barId;
    private String barName;
    private Long userId;
    private String nickName;
    private String title;
    private String contentPreview;
    private String riskLevel;
    private String handlerAction;
    private LocalDateTime createTime;
}
