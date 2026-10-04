package com.lucky.community.vo;

import com.lucky.community.ai.vo.EvidenceVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子详情
 */
@Data
public class PostDetailVO {

    /** 帖子ID */
    private Long postId;

    /** 所属贴吧ID */
    private Long barId;

    /** 贴吧名 */
    private String barName;

    /** 作者user_id */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 图片URL列表，按 sort_no 升序 */
    private List<String> images;

    /** 点赞数 */
    private Long likeCount;

    /** 评论数（含回复） */
    private Long commentCount;

    /** 收藏数 */
    private Long favoriteCount;

    /** 当前用户是否已赞；未登录为 false */
    private Boolean liked;

    /** 当前用户是否已收藏；未登录为 false */
    private Boolean favorited;

    /** AI内容分析状态：0待处理 1处理中 2成功 3失败 */
    private Integer analysisStatus;

    /** AI生成摘要；尚未出结论时为 null */
    private String analysisSummary;

    /** 相似帖子ID；没有召回时为空数组 */
    private List<Long> similarPostIds;

    /** 风险等级：无风险/低/中/高；尚未出结论时为 null */
    private String analysisRiskLevel;

    /** 命中的风险分类；尚未出结论时为 null */
    private List<String> analysisCategories;

    /** 置信度 0~1；尚未出结论时为 null */
    private Double analysisConfidence;

    /** 原文引用；尚未出结论时为 null */
    private List<EvidenceVO> analysisEvidence;

    /** 判定理由；尚未出结论时为 null */
    private String analysisReasoning;

    /** 是否触发过冲突复核 */
    private Boolean analysisHasConflict;

    /** 最高优先级冲突类型 */
    private String analysisConflictType;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
