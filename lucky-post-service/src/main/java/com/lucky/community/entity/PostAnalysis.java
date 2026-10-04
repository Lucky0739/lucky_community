package com.lucky.community.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PostAnalysis {

    private Long id;
    private Long postId;
    private String summary;
    private String similarPostIds;
    private String riskLevel;
    private String riskCategories;
    private BigDecimal confidence;
    private String evidenceJson;
    private String reasoning;
    private Integer hasConflict;
    private String conflictType;
    private Integer retryCount;
    private String handlerAction;
    private String modelVersion;
    private Integer latencyMs;
    private Integer tokenCost;
    private LocalDateTime createTime;
}
