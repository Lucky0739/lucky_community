package com.lucky.community.ai.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 一条原文引用
 * <p>{@code quote} 已在 Python 侧校验过是原文的连续子串，{@code offset} 也是由
 * 服务端按原文重新算的、不是模型给的——这里可以直接信任。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvidenceVO {

    /** 命中的位置：title 或 content */
    private String field;

    /** 原文中逐字存在的连续子串 */
    private String quote;

    /** quote 在该字段原文中的起始下标 */
    private Integer offset;
}
