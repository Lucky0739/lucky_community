package com.lucky.community.vo;

import com.lucky.community.ai.vo.EvidenceVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 帖子详情：本体字段 + AI 审核结论。
 *
 * <p>只有详情接口会返回它。发帖 / 改帖返回 {@link PostBaseVO} ——
 * 那两个时刻还没有结论可填。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostDetailVO extends PostBaseVO {

    /** AI 分析状态 */
    private Integer analysisStatus;

    /** AI 摘要 */
    private String analysisSummary;

    /**
     * 相似帖子 ID。仅作者可见：非作者的响应副本会被显式置空。
     * 共享详情缓存里可能仍持有该值，但绝不写入非作者响应。
     */
    private List<Long> similarPostIds;

    /** 当前请求者是否为作者。仅在 getDetail 的返回副本上填写，不写入详情缓存 */
    private Boolean viewerIsAuthor;

    /** 作者可见的相似帖子摘要，按 AI 排名顺序；非作者恒为 null */
    private List<PostSummaryVO> similarPosts;

    private String analysisRiskLevel;

    private Double analysisConfidence;

    private String analysisReasoning;

    private Boolean analysisHasConflict;

    private String analysisConflictType;

    private List<String> analysisCategories;

    private List<EvidenceVO> analysisEvidence;
}
