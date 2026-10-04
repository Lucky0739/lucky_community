package com.lucky.community.ai.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 风控审核结论（设计文档 4.4 的 RiskAssessment）
 * <p>字段名用 {@code @JsonProperty} 显式映射到 Python 侧的 snake_case。
 * 刻意不让 Spring 全局改成蛇形命名——那会同时改掉所有对外接口的字段名。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskAssessmentVO {

    /** 风险等级：无风险/低/中/高 */
    @JsonProperty("risk_level")
    private String riskLevel;

    /** 命中的风险分类，可能为空列表 */
    private List<String> categories;

    /** 置信度 0~1 */
    private Double confidence;

    /** 原文引用。Python 侧已保证每条的 quote 都是原文的连续子串 */
    private List<EvidenceVO> evidence;

    /** 判定理由 */
    private String reasoning;
}
