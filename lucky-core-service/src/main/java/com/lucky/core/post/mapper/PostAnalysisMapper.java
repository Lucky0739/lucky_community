package com.lucky.core.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lucky.core.post.entity.PostAnalysis;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface PostAnalysisMapper extends BaseMapper<PostAnalysis> {

    /**
     * 按 post_id 幂等写入分析结果
     *
     * <p>用 {@code ON DUPLICATE KEY UPDATE} 而不是先删后插：后者在并发下会短暂出现
     * 「查不到」的窗口，而本表是「一个帖子一条结论」，没必要制造这种不确定性。
     * 文档 6.3 要求「重跑不得产生重复副作用」，唯一键 {@code uk_post_id} 是这条的落点。
     *
     * <p><b>刻意不更新 {@code handler_action} 和 {@code retry_count}</b>：
     * 前者是吧务的人工处置结论，后者是回溯复核的计数——两者都是「人/流程产生的状态」，
     * 不该被一次机器重跑清掉。重跑只更新分析结论本身。
     *
     * <p>用行别名（{@code AS incoming}）而不是 MySQL 的 {@code VALUES()} 函数：
     * 后者自 8.0.20 起已废弃。目标库是 8.0.45，实测该语法可用。
     */
    @Insert("""
            INSERT INTO tb_post_analysis
                (id, post_id, risk_level, risk_categories, confidence, evidence_json,
                 reasoning, has_conflict, conflict_type, retry_count, handler_action,
                 model_version, latency_ms, token_cost)
            VALUES
                (#{a.id}, #{a.postId}, #{a.riskLevel}, #{a.riskCategories}, #{a.confidence},
                 #{a.evidenceJson}, #{a.reasoning}, #{a.hasConflict}, #{a.conflictType},
                 #{a.retryCount}, #{a.handlerAction}, #{a.modelVersion}, #{a.latencyMs},
                 #{a.tokenCost})
            AS incoming
            ON DUPLICATE KEY UPDATE
                risk_level      = incoming.risk_level,
                risk_categories = incoming.risk_categories,
                confidence      = incoming.confidence,
                evidence_json   = incoming.evidence_json,
                reasoning       = incoming.reasoning,
                model_version   = incoming.model_version,
                latency_ms      = incoming.latency_ms,
                token_cost      = incoming.token_cost
            """)
    int upsert(@Param("a") PostAnalysis analysis);
}
