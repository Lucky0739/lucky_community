package com.lucky.community.ai.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Python 审核服务的响应体
 *
 * <p>{@code degraded} 有两种含义，调用方必须分开处理：
 * <ul>
 *   <li>{@code assessment == null}：**没拿到结论**（模型拒答 / 调用失败 / 解析失败）。
 *       按文档 6.2 标记「需人工」，绝不能当成「无风险」放过。</li>
 *   <li>{@code assessment != null}：拿到了结论，但被归一化兜底或引用校验降级过，
 *       {@code degradeReason} 说明原因。结论可用，但值得人工复核。</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiModerationResultVO {

    @JsonProperty("post_id")
    private Long postId;

    /** 为 null 表示没有拿到结论，见类注释 */
    private RiskAssessmentVO assessment;

    /** 帖子摘要，最多100字 */
    private String summary;

    /** 相似帖检索结果 */
    private RetrievalResultVO retrieval;

    @JsonProperty("has_conflict")
    private Boolean hasConflict;

    @JsonProperty("conflict_type")
    private String conflictType;

    @JsonProperty("retry_count")
    private Integer retryCount;

    /** 是否发生了降级 */
    private Boolean degraded;

    /** 降级原因，未降级时为 null */
    @JsonProperty("degrade_reason")
    private String degradeReason;

    /** 实际使用的模型版本 */
    @JsonProperty("model_version")
    private String modelVersion;

    /** 端到端耗时(毫秒) */
    @JsonProperty("latency_ms")
    private Integer latencyMs;

    /** token 消耗 */
    @JsonProperty("token_cost")
    private Integer tokenCost;
}
