package com.lucky.community.ai;

import com.lucky.community.entity.Post;
import com.lucky.community.entity.PostImage;
import com.lucky.community.mapper.PostImageMapper;
import com.lucky.community.mapper.PostMapper;
import com.lucky.community.rpc.KeyValueRpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 读侧：消费 {@code tb_post} 的三个 AI 管道状态列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModerationRetryService {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final KeyValueRpcService keyValueRpcService;
    private final AiModerationService aiModerationService;

    @Value("${ai.moderation.retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${ai.moderation.retry.batch-size:20}")
    private int batchSize;

    /** 自动重试次数上限。达到后 selectRetryable 不再返回该帖，等同转入人工待审 */
    @Value("${ai.moderation.retry.max-retry:3}")
    private int maxRetry;

    /**
     * 扫描并发重投。单条失败只记日志，不中断整轮。
     */
    @Scheduled(fixedDelayString = "${ai.moderation.retry.interval-ms:60000}")
    public void retryFailedPosts() {
        if (!retryEnabled) {
            return;
        }
        List<Post> candidates;
        try {
            candidates = postMapper.selectRetryable(batchSize, maxRetry);
        } catch (Exception e) {
            log.warn("扫描待重试帖子失败", e);
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }
        log.info("待重试 AI 审核的帖子 {} 条", candidates.size());
        for (Post post : candidates) {
            try {
                resubmit(post, true);
            } catch (Exception e) {
                // 单条失败不能拖垮整轮扫描
                log.warn("重试帖子 AI 审核失败，postId={}", post.getId(), e);
            }
        }
    }

    /**
     * 人工手动重跑一条帖：清零重试计数再重投，让自动重试也能重新接管它。
     *
     * @return 是否成功投递（正文读不到或线程池拒绝时为 false）
     */
    public boolean reanalyze(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            return false;
        }
        postMapper.resetAnalysisRetry(postId);
        return resubmit(post, false);
    }

    /**
     * 回读正文与图片后重投审核。
     *
     * @param countAttempt 是否消耗一次自动重试配额。手动重跑不计数（计数已被清零）
     */
    private boolean resubmit(Post post, boolean countAttempt) {
        // 正文在 Cassandra。读不到说明 KV 恰好不可用 —— 本轮跳过，不提交也不计数，下轮再来
        String content = keyValueRpcService.find(post.getContentUuid());
        if (content == null) {
            log.warn("重试时读不到帖子正文，跳过本轮，postId={}", post.getId());
            return false;
        }
        List<String> imageUrls = postImageMapper.selectByPostIds(List.of(post.getId())).stream()
                .map(PostImage::getUrl)
                .toList();
        boolean submitted = aiModerationService.submit(
                post.getId(), post.getBarId(), post.getUserId(), post.getTitle(), content, imageUrls);
        // 被拒绝（线程池满 / 审核关闭）不算一次尝试：不消耗配额，下一轮自然还会捞到它
        if (submitted && countAttempt) {
            postMapper.incrementAnalysisRetry(post.getId());
        }
        return submitted;
    }
}
