package com.lucky.community.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lucky.bloom.LongBloomFilter;
import com.lucky.community.bar.entity.Bar;
import com.lucky.community.bar.entity.BarManager;
import com.lucky.community.bar.mapper.BarManagerMapper;
import com.lucky.community.bar.mapper.BarMapper;
import com.lucky.community.entity.HandlerAction;
import com.lucky.community.cache.PostDetailCache;
import com.lucky.community.cache.PostFeedCache;
import com.lucky.community.dto.CreatePostDTO;
import com.lucky.community.dto.PostFeedQueryDTO;
import com.lucky.community.dto.PostQueryDTO;
import com.lucky.community.dto.UpdatePostDTO;
import com.lucky.community.entity.Post;
import com.lucky.community.entity.PostAnalysis;
import com.lucky.community.entity.PostImage;
import com.lucky.community.support.entity.UserBrief;
import com.lucky.community.mapper.PostAnalysisMapper;
import com.lucky.community.mapper.PostImageMapper;
import com.lucky.community.mapper.PostMapper;
import com.lucky.community.support.mapper.UserReadMapper;
import com.lucky.community.support.PermissionSupport;
import com.lucky.community.ai.AiModerationService;
import com.lucky.community.ai.vo.EvidenceVO;
import com.lucky.common.file.FileService;
import com.lucky.community.service.PostService;
import com.lucky.community.vo.PageVO;
import com.lucky.community.vo.PostDetailVO;
import com.lucky.community.vo.PostSummaryVO;
import com.lucky.community.vo.PostViewVO;
import com.lucky.community.comment.vo.CommentVO;
import com.lucky.community.common.PageResult;
import com.lucky.community.support.CommentViewSupport;
import com.lucky.community.support.PostEngagementSupport;
import cn.hutool.core.util.IdUtil;
import com.lucky.common.auth.UserContext;
import com.lucky.common.constant.PostConstant;
import com.lucky.common.constant.CommentConstant;
import com.lucky.common.dto.PageQueryDTO;
import com.lucky.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 帖子实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    /** 与 tb_post.analysis_status 列注释一致：2 = 分析成功 */
    private static final int ANALYSIS_SUCCESS = 2;

    /** AI 判为无风险，且还没有人工处置时，帖子可以公开 */
    private static final String RISK_NONE = "无风险";

    /**
     * 公开可见：人工放行（未处置、警告），或 AI 判无风险且尚未人工处置。
     * 待审、审核失败、有风险且无人放行的帖子不出现在列表和详情里。
     * 删帖、封禁会把 deleted 置 1，逻辑删除本身就会挡住。
     */

    /**
     * 解析 evidence_json。刻意不注入容器里的 ObjectMapper：那边是缓存二进制格式，
     * 语义混用；EvidenceVO 没有时间字段，裸 JsonMapper 足够。
     */
    private static final ObjectMapper ANALYSIS_JSON = JsonMapper.builder().build();

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostAnalysisMapper postAnalysisMapper;
    private final BarMapper barMapper;
    private final BarManagerMapper barManagerMapper;
    private final UserReadMapper userReadMapper;
    private final FileService fileService;
    private final PostDetailCache postDetailCache;
    private final PostFeedCache postFeedCache;
    private final LongBloomFilter postBloomFilter;
    private final AiModerationService aiModerationService;
    private final PostEngagementSupport postEngagementSupport;
    private final CommentViewSupport commentViewSupport;
    private final PermissionSupport permissionSupport;

    @Qualifier("postImageQueryExecutor")
    private final ThreadPoolTaskExecutor postImageQueryExecutor;

    /** 需要手动取「当前时刻」时统一走它，见 {@link com.lucky.community.config.ClockConfig} */
    private final Clock clock;

    /**
     * 把客户端回传的图片直链反解成 objectName，并按首次出现顺序去重
     * */
    private List<String> resolveImageObjectNames(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        Set<String> objectNames = new LinkedHashSet<>();
        for (String url : urls) {
            String objectName = fileService.resolvePostObjectName(url);
            if (objectName == null) {
                throw new BusinessException("图片地址不合法");
            }
            objectNames.add(objectName);
        }
        return List.copyOf(objectNames);
    }

    /**
     * 发布帖子
     * @param userId
     * @param createPostDTO
     * @return
     */
    @Override
    // 事务，要么同时成功要么同时失败
    @Transactional(rollbackFor = Exception.class)
    public PostDetailVO createPost(Long userId, CreatePostDTO createPostDTO) {
        // 1. 校验贴吧
        Bar bar = barMapper.selectById(createPostDTO.getBarId());
        if (bar == null || bar.getStatus() == null || bar.getStatus() != Bar.STATUS_NORMAL) {
            throw new BusinessException("贴吧不存在或已停用");
        }
        // 2. 图片直链全部反解成 objectName 后去重，再按去重后的条数校验上限
        List<String> objectNames = resolveImageObjectNames(createPostDTO.getImages());
        validateImages(objectNames);
        List<String> imageUrls = objectNames.stream()
                .map(fileService::toPublicUrl)
                .toList();
        // 3. 写库。这一步已经没有任何远程 I/O——图片在预上传时就已经落 MinIO 了，
        Post post = new Post();
        post.setBarId(createPostDTO.getBarId());
        post.setUserId(userId);
        post.setTitle(createPostDTO.getTitle());
        post.setContent(createPostDTO.getContent());
        applyCover(post, imageUrls);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        post.setCreateTime(now);
        post.setUpdateTime(now);
        // 显式置 0 而非依赖 DB 默认值：insert 后 MP 不会回读默认值，
        post.setEventStatus(0);
        post.setEmbedStatus(0);
        post.setAnalysisStatus(0);
        post.setAnalysisRetryCount(0);
        post.setId(IdUtil.getSnowflakeNextId());
        postMapper.insert(post);   // MP 回填雪花ID
        // 放入布隆过滤器
        postBloomFilter.add(post.getId());
        registerAfterCommit(() -> aiModerationService.submit(
                post.getId(), post.getBarId(), post.getUserId(),
                post.getTitle(), post.getContent(), imageUrls));
        // 操纵postImage数据库
        for (int i = 0; i < objectNames.size(); i++) {
            PostImage postImage = new PostImage();
            postImage.setPostId(post.getId());
            postImage.setUrl(imageUrls.get(i));
            postImage.setObjectName(objectNames.get(i));
            postImage.setSortNo(i);
            postImageMapper.insert(postImage);
        }
        // 直接用手上这份 post 组装，不再重查：时间字段已在 insert 前显式填好，
        PostDetailVO postDetailVO = new PostDetailVO();
        postDetailVO.setPostId(post.getId());
        postDetailVO.setBarId(post.getBarId());
        // 无需判空：bar 在上面已经校验过存在且状态正常，这里必然非 null
        postDetailVO.setBarName(bar.getName());
        postDetailVO.setUserId(post.getUserId());
        // 作者可能已被逻辑删除(selectById 自动过滤)，渲染为 null 而不是让请求失败
        postDetailVO.setTitle(post.getTitle());
        postDetailVO.setContent(post.getContent());
        postDetailVO.setImages(imageUrls);
        postDetailVO.setAnalysisStatus(post.getAnalysisStatus());
        // 不填分析结论字段：刚发的帖子必然 analysis_status=0，没有结论可填
        postDetailVO.setCreateTime(post.getCreateTime());
        postDetailVO.setUpdateTime(post.getUpdateTime());
        fillDetailInteraction(postDetailVO);
        return postDetailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostDetailVO updatePost(Long operatorId, Long postId, UpdatePostDTO updatePostDTO) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!post.getUserId().equals(operatorId)) {
            throw new BusinessException("只能修改自己的帖子");
        }
        List<String> objectNames = resolveImageObjectNames(updatePostDTO.getImages());
        validateImages(objectNames);
        List<String> urls = objectNames.stream().map(fileService::toPublicUrl).toList();

        boolean textChanged = !Objects.equals(post.getTitle(), updatePostDTO.getTitle());
        if (!Objects.equals(post.getContent(), updatePostDTO.getContent())) {
            textChanged = true;
        }
        Post patch = new Post();
        patch.setId(postId);
        patch.setTitle(updatePostDTO.getTitle());
        patch.setContent(updatePostDTO.getContent());
        patch.setCoverImage(coverOf(urls));
        patch.setImageCount(urls.size());
        postMapper.updateContent(patch);
        if (textChanged) {
            postMapper.resetAnalysisOnEdit(postId);
        }

        postImageMapper.logicalDeleteByPostId(postId);
        for (int i = 0; i < objectNames.size(); i++) {
            PostImage row = new PostImage();
            row.setPostId(postId);
            row.setUrl(urls.get(i));
            row.setObjectName(objectNames.get(i));
            row.setSortNo(i);
            postImageMapper.insert(row);
        }
        if (textChanged) {
            registerAfterCommit(() -> aiModerationService.submit(
                    postId, post.getBarId(), post.getUserId(),
                    updatePostDTO.getTitle(), updatePostDTO.getContent(), urls));
        }
        registerAfterCommit(() -> postDetailCache.evict(postId));
        Post saved = postMapper.selectById(postId);
        if (saved == null) {
            throw new BusinessException("帖子不存在");
        }
        Bar bar = barMapper.selectById(saved.getBarId());
        UserBrief author = userReadMapper.selectById(saved.getUserId());
        PostAnalysis analysis = loadAnalysisIfSuccess(saved);
        PostDetailVO postDetailVO = assembleDetail(saved, bar, author, urls, analysis);
        fillDetailInteraction(postDetailVO);
        return postDetailVO;
    }

    // 删除帖子业务逻辑，删除Caffeine和Redis放到事务执行完后执行
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long operatorId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!post.getUserId().equals(operatorId) && !canModerate(operatorId, post.getBarId())) {
            throw new BusinessException("无权删除该帖子");
        }
        // @TableLogic：deleteById 只把 deleted 置 1，不物理删行
        postMapper.logicalDeleteById(postId);
        postImageMapper.logicalDeleteByPostId(postId);
        registerAfterCommit(() -> postDetailCache.evict(postId));
        registerAfterCommit(() -> aiModerationService.deleteVector(postId));
    }

    /**
     * 把一段收尾动作挂到事务提交之后执行
     */
    private void registerAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 理论上走不到：createPost 带 @Transactional。真走到了说明事务传播被改过，
            log.warn("无活动事务，直接执行提交后动作");
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

    /**
     * 查看帖子详情
     */
    @Override
    public PostDetailVO getDetail(Long postId) {
        PostDetailVO detail = postDetailCache.get(postId, this::loadDetail);
        if (detail == null) {
            throw new BusinessException("帖子不存在");
        }
        PostDetailVO postDetailVO = new PostDetailVO();
        BeanUtils.copyProperties(detail, postDetailVO);
        fillDetailInteraction(postDetailVO);
        return postDetailVO;
    }

    @Override
    public PostViewVO getView(Long postId) {
        PostDetailVO post = getDetail(postId);
        CommentViewSupport.RootCommentPage comments = commentViewSupport.loadRootFirstPage(
                postId, CommentConstant.FIRST_PAGE_SIZE);
        PostViewVO postViewVO = new PostViewVO();
        postViewVO.setPost(post);
        postViewVO.setComments(comments.records());
        postViewVO.setRootCommentTotal(comments.total());
        return postViewVO;
    }

    /**
     * 回源查库（缓存未命中时执行）
     */
    private PostDetailVO loadDetail(Long postId) {
        // 一定不存在 → 直接短路，一条 SQL 都不打。这是防缓存穿透的关键一步
        if (!postBloomFilter.mightContain(postId)) {
            return null;
        }
        // 其余情况都要查库：过滤器返回 true 只代表「可能存在」，也可能是假阳性
        Post post = postMapper.selectById(postId);
        if (post == null || !isPubliclyVisible(postId)) {
            return null;
        }
        // 图片与贴吧/作者/分析互不依赖：先把图片查询丢进线程池，本线程继续查其余表。
        CompletableFuture<List<String>> imageUrls = loadImageUrlsAsync(post);
        Bar bar = barMapper.selectById(post.getBarId());
        UserBrief author = userReadMapper.selectById(post.getUserId());
        PostAnalysis analysis = loadAnalysisIfSuccess(post);
        return assembleDetail(post, bar, author, joinImageUrls(imageUrls), analysis);
    }

    @Override
    public PageResult<PostSummaryVO> pagePosts(PostQueryDTO query) {
        Integer pageNum = query.getPageNum();
        Integer pageSize = query.getPageSize();
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        long total = postMapper.countApprovedPage(query.getBarId(), query.getUserId());
        List<Post> posts = postMapper.selectApprovedPage(query.getBarId(), query.getUserId(), ps, (pn - 1) * ps);
        PageResult<PostSummaryVO> result = new PageResult<>();
        result.setPageNum(pn);
        result.setPageSize(ps);
        result.setTotal(total);
        if (posts.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(userIds.stream().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectByIds(barIds.stream().toList()).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));
        result.setRecords(posts.stream()
                .map(post -> toSummaryVO(post,
                        barById.get(post.getBarId()),
                        userById.get(post.getUserId())))
                .toList());
        fillSummaryInteraction(result.getRecords());
        return result;
    }

    @Override
    public PageVO<PostSummaryVO> feedPosts(PostFeedQueryDTO query) {
        // 这里自己夹一道：size 不像 pageSize 有分页插件的 maxLimit 兜底，
        // 而校验注解只覆盖走 HTTP 进来的请求，服务被内部调用时不受它保护
        Integer requestedSize = query.getSize();
        int size = requestedSize == null
                ? PageQueryDTO.DEFAULT_SIZE
                : Math.min(Math.max(requestedSize, 1), PageQueryDTO.MAX_SIZE);

        List<PostSummaryVO> records;
        if (query.getLastId() == null) {
            // 首屏走本机缓存。缓存里那份的 liked/favorited 恒为 null，
            // 而 getFirstPage 返回的是副本——下面填互动字段污染不到缓存
            String filterKey = PostFeedCache.filterKey(query.getBarId(), query.getUserId(), size);
            records = postFeedCache.getFirstPage(filterKey,
                    () -> assembleFeedBase(query.getBarId(), query.getUserId(), null, size));
        } else {
            // 翻页不走缓存：游标的组合是无穷多的，缓存命中率趋近于零，只会白占内存。
            // 而且翻页查询本身就是等值索引扫描，没有缓存的必要
            records = assembleFeedBase(query.getBarId(), query.getUserId(), query.getLastId(), size);
        }

        fillSummaryInteraction(records);

        PageVO<PostSummaryVO> result = new PageVO<>();
        result.setRecords(records);
        // 满一页才给下一页游标。没有多取一条来判断「还有没有下一条」，
        // 因为那会破坏「缓存与直查返回同一形状」的简洁性——数据恰好是 size 整数倍时
        // 客户端会多收到一次空数组，这个代价可以接受
        result.setNextCursor(records.size() < size
                ? null
                : records.get(records.size() - 1).getPostId());
        return result;
    }

    /**
     * 按游标条件取一页并组装成<b>基础 VO</b>——互动字段留空，由调用方填
     */
    private List<PostSummaryVO> assembleFeedBase(Long barId, Long userId, Long lastId, int size) {
        List<Post> posts = postMapper.selectApprovedFeed(barId, userId, lastId, size);
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(userIds.stream().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectByIds(barIds.stream().toList()).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));
        return posts.stream()
                .map(post -> toSummaryVO(post,
                        barById.get(post.getBarId()),
                        userById.get(post.getUserId())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public int warmUpRecentPosts(int limit) {
        if (limit <= 0) {
            return 0;
        }
        // 用 last("LIMIT n") 而不是 selectPage：后者会多发一条 COUNT，
        // 预热不需要总数。n 是配置里的整型，不存在注入面
        int safeLimit = Math.min(limit, PostConstant.MAX_WARM_UP_LIMIT);
        List<Post> posts = postMapper.selectApprovedRecent(safeLimit);
        if (posts.isEmpty()) {
            return 0;
        }
        // 与 pagePosts 同样的批量取关联，再加一次分析结论，避免逐帖回源
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());

        Map<Long, List<PostImage>> imagesByPostId = listImagesByPostIds(postIds);
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(userIds.stream().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectByIds(barIds.stream().toList()).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));
        Map<Long, PostAnalysis> analysisByPostId = listAnalysisByPostIds(postIds);

        for (Post post : posts) {
            List<PostImage> images = imagesByPostId.getOrDefault(post.getId(), List.of());
            postDetailCache.put(assembleDetail(post,
                    barById.get(post.getBarId()),
                    userById.get(post.getUserId()),
                    images.stream().map(PostImage::getUrl).toList(),
                    analysisByPostId.get(post.getId())));
        }
        return posts.size();
    }

    /**
     * 图片数量兜底
     */
    private void validateImages(List<String> objectNames) {
        if (objectNames.size() > PostConstant.MAX_IMAGE_COUNT) {
            throw new BusinessException("最多上传" + PostConstant.MAX_IMAGE_COUNT + "张图片");
        }
    }

    private boolean canModerate(Long userId, Long barId) {
        if (permissionSupport.isPlatformAdmin(userId)) {
            return true;
        }
        Bar bar = barMapper.selectById(barId);
        if (bar != null && userId.equals(bar.getOwnerUserId())) {
            return true;
        }
        return barManagerMapper.countActiveByBarAndUser(barId, userId, BarManager.STATUS_ACTIVE) > 0;
    }

    /**
     * 详情回源：异步查图片表。无图帖连这条 SQL 都不打。
     */
    private CompletableFuture<List<String>> loadImageUrlsAsync(Post post) {
        if (post.getImageCount() == null || post.getImageCount() == 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        Long postId = post.getId();
        return CompletableFuture.supplyAsync(
                () -> listImagesByPostIds(List.of(postId))
                        .getOrDefault(postId, List.of())
                        .stream()
                        .map(PostImage::getUrl)
                        .toList(),
                postImageQueryExecutor);
    }

    private List<String> joinImageUrls(CompletableFuture<List<String>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException("加载帖子图片失败");
        }
    }

    /**
     * 按帖子ID批量取图片，一次查询取回后内存分组，避免逐帖查库(N+1)
     * <p>查询已按 sort_no 升序，分组时各组内部保持该顺序
     */
    private Map<Long, List<PostImage>> listImagesByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return postImageMapper.selectByPostIds(new ArrayList<>(postIds)).stream()
                .collect(Collectors.groupingBy(PostImage::getPostId));
    }

    private PostDetailVO assembleDetail(Post post, Bar bar, UserBrief author, List<String> imageUrls,
                                       PostAnalysis analysis) {
        PostDetailVO postDetailVO = new PostDetailVO();
        postDetailVO.setPostId(post.getId());
        postDetailVO.setBarId(post.getBarId());
        postDetailVO.setBarName(bar == null ? null : bar.getName());
        postDetailVO.setUserId(post.getUserId());
        // 作者可能已被逻辑删除(selectById 自动过滤)，渲染为 null 而不是让请求失败
     //   vo.setNickName(author == null ? null : author.getNickName());
      //  vo.setIcon(author == null ? null : author.getIcon());
        postDetailVO.setTitle(post.getTitle());
        postDetailVO.setContent(post.getContent());
        postDetailVO.setImages(imageUrls);
        postDetailVO.setAnalysisStatus(post.getAnalysisStatus());
        fillAnalysis(postDetailVO, analysis);
        postDetailVO.setCreateTime(post.getCreateTime());
        postDetailVO.setUpdateTime(post.getUpdateTime());
        return postDetailVO;
    }

    /**
     * 公开可见：人工放行，或 AI 已给出「无风险」且还没有人工处置。
     */
    private boolean isPubliclyVisible(Long postId) {
        return postAnalysisMapper.countPubliclyVisible(postId) > 0;
    }

    private PostAnalysis loadAnalysisIfSuccess(Post post) {
        if (post.getAnalysisStatus() == null || post.getAnalysisStatus() != ANALYSIS_SUCCESS) {
            return null;
        }
        return postAnalysisMapper.selectByPostId(post.getId());
    }

    private Map<Long, PostAnalysis> listAnalysisByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return postAnalysisMapper.selectByPostIds(new ArrayList<>(postIds)).stream()
                .collect(Collectors.toMap(PostAnalysis::getPostId, Function.identity(), (a, b) -> a));
    }

    private void fillAnalysis(PostDetailVO vo, PostAnalysis analysis) {
        if (analysis == null) {
            return;
        }
        vo.setAnalysisSummary(analysis.getSummary());
        vo.setSimilarPostIds(parseSimilarPostIds(analysis.getSimilarPostIds()));
        vo.setAnalysisRiskLevel(analysis.getRiskLevel());
        vo.setAnalysisConfidence(analysis.getConfidence() == null ? null : analysis.getConfidence().doubleValue());
        vo.setAnalysisReasoning(analysis.getReasoning());
        vo.setAnalysisHasConflict(Integer.valueOf(1).equals(analysis.getHasConflict()));
        vo.setAnalysisConflictType(analysis.getConflictType());
        if (analysis.getRiskCategories() == null || analysis.getRiskCategories().isBlank()) {
            vo.setAnalysisCategories(List.of());
        } else {
            vo.setAnalysisCategories(List.of(analysis.getRiskCategories().split(",")));
        }
        vo.setAnalysisEvidence(parseEvidence(analysis.getEvidenceJson()));
    }

    private List<Long> parseSimilarPostIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = ANALYSIS_JSON.readValue(json, new TypeReference<>() {});
            return ids == null ? List.of() : ids;
        } catch (Exception e) {
            log.warn("解析相似帖子ID失败", e);
            return List.of();
        }
    }

    private List<EvidenceVO> parseEvidence(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<EvidenceVO> evidence = ANALYSIS_JSON.readValue(json, new TypeReference<>() {});
            return evidence == null ? List.of() : evidence;
        } catch (Exception e) {
            log.warn("解析审核证据失败", e);
            return List.of();
        }
    }

    private PostSummaryVO toSummaryVO(Post post, Bar bar, UserBrief author) {
        PostSummaryVO vo = new PostSummaryVO();
        vo.setPostId(post.getId());
        vo.setBarId(post.getBarId());
        vo.setBarName(bar == null ? null : bar.getName());
        vo.setUserId(post.getUserId());
        // 作者已被删除时 selectBatchIds 查不到，此处渲染为 null，不让整个列表 500
        vo.setNickName(author == null ? null : author.getNickName());
        vo.setIcon(author == null ? null : author.getIcon());
        vo.setTitle(post.getTitle());
        vo.setContentPreview(preview(post.getContent()));
        vo.setCoverImage(post.getCoverImage());
        vo.setImageCount(post.getImageCount() == null ? 0 : post.getImageCount());
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }

    /** 第一张图即封面；无图为 null */
    private static String coverOf(List<String> imageUrls) {
        return imageUrls.isEmpty() ? null : imageUrls.get(0);
    }

    /** 发帖时把封面与张数写进主表，与图片表保持同一口径 */
    private static void applyCover(Post post, List<String> imageUrls) {
        post.setCoverImage(coverOf(imageUrls));
        post.setImageCount(imageUrls.size());
    }

    /**
     * 正文预览：按码点截断，避免 substring 劈开代理对(emoji)产生乱码
     */
    private String preview(String content) {
        if (content == null) {
            return null;
        }
        int[] codePoints = content.codePoints().toArray();
        if (codePoints.length <= PostConstant.PREVIEW_CODE_POINTS) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PostConstant.PREVIEW_CODE_POINTS; i++) {
            sb.appendCodePoint(codePoints[i]);
        }
        return sb.append("...").toString();
    }

    /**
     * 互动数据不进帖子详情缓存：赞/评/藏变更频繁，且 liked/favorited 是用户维度。
     */
    private void fillDetailInteraction(PostDetailVO postDetailVO) {
        Long postId = postDetailVO.getPostId();
        List<Long> ids = List.of(postId);
        Long userId = UserContext.getUserId();
        postDetailVO.setLikeCount(postEngagementSupport.getLikeCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setCommentCount(postEngagementSupport.getCommentCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setFavoriteCount(postEngagementSupport.getFavoriteCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setLiked(userId != null && postEngagementSupport.listLikedPostIds(userId, ids).contains(postId));
        postDetailVO.setFavorited(userId != null && postEngagementSupport.listFavoritedPostIds(userId, ids).contains(postId));
    }

    private void fillSummaryInteraction(List<PostSummaryVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(PostSummaryVO::getPostId).toList();
        Long userId = UserContext.getUserId();
        Map<Long, Long> likes = postEngagementSupport.getLikeCounts(ids);
        Map<Long, Long> comments = postEngagementSupport.getCommentCounts(ids);
        Map<Long, Long> favorites = postEngagementSupport.getFavoriteCounts(ids);
        Set<Long> liked = userId == null ? Set.of() : postEngagementSupport.listLikedPostIds(userId, ids);
        Set<Long> favorited = userId == null ? Set.of() : postEngagementSupport.listFavoritedPostIds(userId, ids);
        for (PostSummaryVO row : rows) {
            Long id = row.getPostId();
            row.setLikeCount(likes.getOrDefault(id, 0L));
            row.setCommentCount(comments.getOrDefault(id, 0L));
            row.setFavoriteCount(favorites.getOrDefault(id, 0L));
            row.setLiked(liked.contains(id));
            row.setFavorited(favorited.contains(id));
        }
    }
}
