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
    /** AI 管道三状态，取值见 com.lucky.community.ai.PostAiStatus。用于排查失败帖 */
    private Integer eventStatus;
    private Integer embedStatus;
    private Integer analysisStatus;
    /** 已消耗的自动重试次数。达到上限即「自动重试已用尽，待人工」 */
    private Integer analysisRetryCount;
    private LocalDateTime createTime;
}
