package com.lucky.community.ai;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lucky.community.ai.dto.AiModerationRequest;
import com.lucky.community.cache.PostDetailCache;
import com.lucky.community.entity.PostAnalysis;
import com.lucky.community.mapper.PostAnalysisMapper;
import com.lucky.community.mapper.PostMapper;
import com.lucky.community.ai.vo.AiModerationResultVO;
import com.lucky.community.ai.vo.RiskAssessmentVO;
import com.lucky.community.ai.vo.SimilarPostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * 调用 Python 审核服务并把结果落库
 */
@Slf4j
@Service
public class AiModerationService {

    /** 内容分析状态：处理中。取值与 tb_post.analysis_status 的列注释一致 */
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_SUCCESS = 2;
    private static final int STATUS_FAILED = 3;

    /**
     * 序列化 evidence_json 用。
     */
    private final ObjectMapper evidenceMapper = JsonMapper.builder().build();

    private final RestClient restClient;
    private final ThreadPoolTaskExecutor executor;
    private final PostMapper postMapper;
    private final PostAnalysisMapper postAnalysisMapper;
    private final PostDetailCache postDetailCache;
    private final boolean enabled;

    public AiModerationService(
            @Qualifier("aiModerationRestClient") RestClient restClient,
            @Qualifier("aiModerationExecutor") ThreadPoolTaskExecutor executor,
            PostMapper postMapper,
            PostAnalysisMapper postAnalysisMapper,
            PostDetailCache postDetailCache,
            @Value("${ai.moderation.enabled:true}") boolean enabled) {
        this.restClient = restClient;
        this.executor = executor;
        this.postMapper = postMapper;
        this.postAnalysisMapper = postAnalysisMapper;
        this.postDetailCache = postDetailCache;
        this.enabled = enabled;
    }

    /**
     * 提交一条帖子的审核任务
     */
    public boolean submit(Long postId, Long barId, Long userId, String title,
                          String content, List<String> imageUrls) {
        if (!enabled) {
            log.debug("AI 审核已关闭，跳过投递，postId={}", postId);
            return false;
        }
        try {
            executor.execute(() -> analyze(postId, barId, userId, title, content, imageUrls));
            return true;
        } catch (RejectedExecutionException e) {
            // 被拒绝意味着任务永远不会执行，状态会一直停在「待处理」(0)。
            // 必须主动置为失败，否则 AI 侧扫待办会反复把它捞出来却永远处理不掉。
            log.warn("审核线程池已满，任务被拒绝，postId={}", postId);
            markStatus(postId, STATUS_FAILED);
            return false;
        }
    }

    /**
     * 后台线程执行体。**不抛异常**——抛出会被线程池吞掉且无人知晓。
     */
    private void analyze(Long postId, Long barId, Long userId, String title,
                         String content, List<String> imageUrls) {
        markStatus(postId, STATUS_PROCESSING);

        AiModerationResultVO result;
        try {
            result = restClient.post()
                    .uri("/govern")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiModerationRequest(
                            postId, barId, userId, title, content,
                            imageUrls == null ? List.of() : imageUrls))
                    .retrieve()
                    .body(AiModerationResultVO.class);
        } catch (Exception e) {
            log.warn("调用 AI 审核服务失败，postId={}，原因={}", postId, e.toString());
            markStatus(postId, STATUS_FAILED);
            return;
        }

        if (result == null) {
            log.warn("AI 审核服务返回空响应体，postId={}", postId);
            markStatus(postId, STATUS_FAILED);
            return;
        }
        // assessment 为空 = 没拿到结论（模型拒答/解析失败）。
        // 按文档 6.2 标记需人工：置为失败，而不是当成「无风险」放过。
        if (result.getAssessment() == null) {
            log.warn("AI 审核未给出结论，postId={}，原因={}", postId, result.getDegradeReason());
            markStatus(postId, STATUS_FAILED);
            return;
        }
        try {
            persist(postId, result);
            markStatus(postId, STATUS_SUCCESS);
            if (Boolean.TRUE.equals(result.getDegraded())) {
                // 有结论但被降级过（归一化兜底或引用不实），落库了但值得人工看一眼
                log.warn("AI 审核结论被降级，postId={}，原因={}", postId, result.getDegradeReason());
            } else {
                log.info("AI 审核完成，postId={}，风险等级={}，耗时={}ms",
                        postId, result.getAssessment().getRiskLevel(), result.getLatencyMs());
            }
        } catch (Exception e) {
            log.error("AI 审核结果落库失败，postId={}", postId, e);
            markStatus(postId, STATUS_FAILED);
        }
    }

    /**
     * 写 tb_post_analysis。走唯一键 upsert，重跑不会留下第二条结论。
     */
    private void persist(Long postId, AiModerationResultVO result) throws JsonProcessingException {
        RiskAssessmentVO assessment = result.getAssessment();

        PostAnalysis row = new PostAnalysis();
        // 自定义 insert 绕过了 MP 的 ASSIGN_ID，必须自己填雪花ID
        row.setId(IdUtil.getSnowflakeNextId());
        row.setPostId(postId);
        row.setSummary(result.getSummary());
        List<Long> similarPostIds = result.getRetrieval() == null
                || result.getRetrieval().getSimilarPosts() == null
                ? List.of()
                : result.getRetrieval().getSimilarPosts().stream()
                        .map(SimilarPostVO::getPostId)
                        .toList();
        row.setSimilarPostIds(evidenceMapper.writeValueAsString(similarPostIds));
        row.setRiskLevel(assessment.getRiskLevel());
        row.setRiskCategories(
                assessment.getCategories() == null ? "" : String.join(",", assessment.getCategories()));
        row.setConfidence(BigDecimal.valueOf(
                assessment.getConfidence() == null ? 0d : assessment.getConfidence()));
        row.setEvidenceJson(evidenceMapper.writeValueAsString(
                assessment.getEvidence() == null ? List.of() : assessment.getEvidence()));
        row.setReasoning(assessment.getReasoning());
        row.setHasConflict(Boolean.TRUE.equals(result.getHasConflict()) ? 1 : 0);
        row.setConflictType(result.getConflictType());
        row.setRetryCount(result.getRetryCount() == null ? 0 : result.getRetryCount());
        row.setModelVersion(result.getModelVersion());
        row.setLatencyMs(result.getLatencyMs());
        row.setTokenCost(result.getTokenCost());

        postAnalysisMapper.upsert(row);
    }

    /** 删帖后尽力清理 AI 自有向量；失败不影响删帖。 */
    public void deleteVector(Long postId) {
        executeBestEffort("删除帖子向量", postId, () -> restClient.delete()
                .uri("/vectors/{postId}", postId)
                .retrieve()
                .toBodilessEntity());
    }

    /** 吧务处置后同步判例动作；失败只影响后续冲突判断。 */
    public void updateHistoricalAction(Long postId, String action) {
        executeBestEffort("同步历史处置", postId, () -> restClient.put()
                .uri(builder -> builder.path("/vectors/{postId}/action")
                        .queryParam("action", action)
                        .build(postId))
                .retrieve()
                .toBodilessEntity());
    }

    private void executeBestEffort(String operation, Long postId, Runnable action) {
        try {
            executor.execute(() -> {
                try {
                    action.run();
                } catch (Exception e) {
                    log.warn("{}失败，postId={}，原因={}", operation, postId, e.toString());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("{}任务被拒绝，postId={}", operation, postId);
        }
    }

    /**
     * 更新 tb_post.analysis_status
     */
    private void markStatus(Long postId, int status) {
        try {
            LocalDateTime analysisTime = status == STATUS_SUCCESS ? LocalDateTime.now() : null;
            postMapper.updateAnalysisStatus(postId, status, analysisTime);
        } catch (Exception e) {
            log.warn("更新 AI 审核状态失败，postId={}，目标状态={}", postId, status, e);
        } finally {
            // 详情缓存里带着 analysis_status / 风险字段；状态一变就必须踢掉，
            // 否则用户会一直看到「待处理」。清缓存失败只影响刷新时机，不影响正确性。
            try {
                postDetailCache.evict(postId);
            } catch (Exception e) {
                log.warn("审核完成后清帖子缓存失败，postId={}", postId, e);
            }
        }
    }
}
