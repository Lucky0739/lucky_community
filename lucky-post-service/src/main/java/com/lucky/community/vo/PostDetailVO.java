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

    /** 相似帖子 ID */
    private List<Long> similarPostIds;

    private String analysisRiskLevel;

    private Double analysisConfidence;

    private String analysisReasoning;

    private Boolean analysisHasConflict;

    private String analysisConflictType;

    private List<String> analysisCategories;

    private List<EvidenceVO> analysisEvidence;
}
