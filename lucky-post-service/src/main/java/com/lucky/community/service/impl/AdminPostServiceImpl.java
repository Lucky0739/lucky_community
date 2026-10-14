package com.lucky.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.vo.PageResult;
import com.lucky.community.ai.AiModerationRetryService;
import com.lucky.community.ai.AiModerationService;
import com.lucky.community.cache.PostDetailCache;
import com.lucky.community.dto.AdminPostQueryDTO;
import com.lucky.community.dto.AdminReviewPostDTO;
import com.lucky.community.entity.Bar;
import com.lucky.community.entity.Post;
import com.lucky.community.entity.PostAnalysis;
import com.lucky.community.mapper.BarMapper;
import com.lucky.community.mapper.PostAnalysisMapper;
import com.lucky.community.mapper.PostImageMapper;
import com.lucky.community.mapper.PostMapper;
import com.lucky.community.service.AdminPostService;
import com.lucky.community.support.PermissionSupport;
import com.lucky.community.support.entity.UserBrief;
import com.lucky.community.support.mapper.UserReadMapper;
import com.lucky.community.vo.AdminPostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPostServiceImpl implements AdminPostService {

    private static final Set<String> REVIEW_ACTIONS = Set.of("未处置", "警告", "删帖", "封禁");

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostAnalysisMapper postAnalysisMapper;
    private final BarMapper barMapper;
    private final UserReadMapper userReadMapper;
    private final PostDetailCache postDetailCache;
    private final AiModerationService aiModerationService;
    private final AiModerationRetryService aiModerationRetryService;
    private final PermissionSupport permissionSupport;

    /**
     * 分页查询帖子
     * @param operatorId
     * @param query
     * @return
     */
    @Override
    public PageResult<AdminPostVO> pagePosts(Long operatorId, AdminPostQueryDTO query) {
        requirePlatformAdmin(operatorId);
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        long total = postMapper.countAdminPage(
                query.getBarId(), query.getPending(), query.getAnalysisFailed());
        PageResult<AdminPostVO> result = new PageResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(total);
        if ((long) (pageNum - 1) * pageSize >= total) {
            result.setRecords(List.of());
            return result;
        }
        List<Post> posts = postMapper.selectAdminPage(
                query.getBarId(), query.getPending(), query.getAnalysisFailed(),
                pageSize, (pageNum - 1) * pageSize);
        result.setRecords(toAdminPosts(posts));
        return result;
    }

    /**
     * 人工审核帖子
     * @param operatorId
     * @param adminReviewPostDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPost(Long operatorId, AdminReviewPostDTO adminReviewPostDTO) {
        // 检验是否管理员
        requirePlatformAdmin(operatorId);
        String action = adminReviewPostDTO.getHandlerAction().trim();
        if (!REVIEW_ACTIONS.contains(action)) {
            throw new BusinessException("审核结论只能是未处置、警告、删帖、封禁");
        }
        Post post = postMapper.selectById(adminReviewPostDTO.getPostId());
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        postAnalysisMapper.saveHandlerAction(IdUtil.getSnowflakeNextId(), post.getId(), action);
        if ("删帖".equals(action) || "封禁".equals(action)) {
            postMapper.logicalDeleteById(post.getId());
            postImageMapper.logicalDeleteByPostId(post.getId());
            runAfterCommit(() -> aiModerationService.deleteVector(post.getId()));
        }
        runAfterCommit(() -> postDetailCache.evict(post.getId()));
    }

    /**
     * 删除帖子
     * @param operatorId
     * @param postId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long operatorId, Long postId) {
        // 检验是否管理员
        requirePlatformAdmin(operatorId);
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        postMapper.logicalDeleteById(postId);
        postImageMapper.logicalDeleteByPostId(postId);
        runAfterCommit(() -> postDetailCache.evict(postId));
        runAfterCommit(() -> aiModerationService.deleteVector(postId));
    }

    /**
     * 手动重跑 AI 审核。用于自动重试已用尽的失败帖。
     *
     * <p>不在事务里：重投本身只是往线程池投递 + 清零计数。投递成功后由
     * {@link AiModerationService} 异步回写状态，不阻塞后台请求。
     */
    @Override
    public void reanalyze(Long operatorId, Long postId) {
        requirePlatformAdmin(operatorId);
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!aiModerationRetryService.reanalyze(postId)) {
            // 正文读不到 / 线程池满 / 审核关闭。计数已在内部清零，用户可稍后重试
            throw new BusinessException("重投 AI 审核失败，请稍后重试");
        }
    }

    private List<AdminPostVO> toAdminPosts(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(userIds.stream().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectByIds(barIds.stream().toList()).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));
        Map<Long, PostAnalysis> analysisByPostId = postAnalysisMapper.selectByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostAnalysis::getPostId, Function.identity(), (left, right) -> left));
        List<AdminPostVO> rows = new ArrayList<>();
        for (Post post : posts) {
            AdminPostVO row = new AdminPostVO();
            row.setPostId(post.getId());
            row.setBarId(post.getBarId());
            Bar bar = barById.get(post.getBarId());
            row.setBarName(bar == null ? null : bar.getName());
            row.setUserId(post.getUserId());
            UserBrief author = userById.get(post.getUserId());
            row.setNickName(author == null ? null : author.getNickName());
            row.setTitle(post.getTitle());
            row.setContentPreview(post.getContentPreview());
            row.setEventStatus(post.getEventStatus());
            row.setEmbedStatus(post.getEmbedStatus());
            row.setAnalysisStatus(post.getAnalysisStatus());
            row.setAnalysisRetryCount(post.getAnalysisRetryCount());
            PostAnalysis analysis = analysisByPostId.get(post.getId());
            if (analysis != null) {
                row.setRiskLevel(analysis.getRiskLevel());
                row.setHandlerAction(analysis.getHandlerAction());
            }
            row.setCreateTime(post.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    private void requirePlatformAdmin(Long operatorId) {
        if (!permissionSupport.isPlatformAdmin(operatorId)) {
            throw new BusinessException("仅平台管理员可访问");
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
