package com.lucky.community.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lucky.bloom.LongBloomFilter;
import com.lucky.community.entity.Bar;
import com.lucky.community.entity.BarManager;
import com.lucky.community.mapper.BarManagerMapper;
import com.lucky.community.mapper.BarFollowReadMapper;
import com.lucky.community.mapper.BarMapper;
import com.lucky.community.entity.HandlerAction;
import com.lucky.community.cache.BarDetailCache;
import com.lucky.community.cache.BarPageCache;
import com.lucky.community.cache.PostDetailCache;
import com.lucky.community.cache.PostFeedCache;
import com.lucky.community.dto.CreatePostDTO;
import com.lucky.community.dto.PostFeedQueryDTO;
import com.lucky.community.dto.UpdatePostDTO;
import com.lucky.community.entity.Post;
import com.lucky.community.entity.PostAnalysis;
import com.lucky.community.entity.PostImage;
import com.lucky.community.support.entity.BarPostCountRow;
import com.lucky.community.support.entity.UserBrief;
import com.lucky.community.mapper.PostAnalysisMapper;
import com.lucky.community.mapper.PostImageMapper;
import com.lucky.community.mapper.PostMapper;
import com.lucky.community.support.mapper.UserReadMapper;
import com.lucky.community.support.PermissionSupport;
import com.lucky.community.rpc.KeyValueRpcService;
import com.lucky.community.ai.AiModerationService;
import com.lucky.community.ai.vo.EvidenceVO;
import com.lucky.community.bloom.PostBloomFilterRebuildJob;
import com.lucky.community.content.PostContentOutboxService;
import com.lucky.community.mq.BarFeedMqSender;
import com.lucky.community.mq.PostIndexEmitter;
import com.lucky.common.file.FileService;
import com.lucky.community.service.PostService;
import com.lucky.community.vo.PageVO;
import com.lucky.community.vo.PostBaseVO;
import com.lucky.community.vo.PostDetailVO;
import com.lucky.community.vo.PostModerationStatusVO;
import com.lucky.community.vo.PostSummaryVO;
import com.lucky.common.vo.PageResult;
import com.lucky.community.support.PostEngagementSupport;
import cn.hutool.core.util.IdUtil;
import com.lucky.common.auth.UserContext;
import com.lucky.common.constant.PostConstant;
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
import org.springframework.util.StringUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collection;
import java.util.HashMap;
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

    /** 作者相似帖最多展示条数，AI 侧 retrieval_k 默认 10，留一点余量 */
    private static final int MAX_SIMILAR_POSTS = 20;

    private static final String RISK_NONE = "无风险";

    /**
     * 解析 evidence_json。刻意不注入容器里的 ObjectMapper：那边是缓存二进制格式，
     * 语义混用；EvidenceVO 没有时间字段，裸 JsonMapper 足够。
     */
    private static final ObjectMapper ANALYSIS_JSON = JsonMapper.builder().build();

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostAnalysisMapper postAnalysisMapper;
    private final BarMapper barMapper;
    private final BarFollowReadMapper barFollowReadMapper;
    private final BarManagerMapper barManagerMapper;
    private final UserReadMapper userReadMapper;
    private final FileService fileService;
    private final PostDetailCache postDetailCache;
    private final PostFeedCache postFeedCache;
    private final BarDetailCache barDetailCache;
    private final BarPageCache barPageCache;
    private final LongBloomFilter postBloomFilter;
    private final PostBloomFilterRebuildJob postBloomFilterRebuildJob;
    private final AiModerationService aiModerationService;
    private final PostEngagementSupport postEngagementSupport;
    private final PermissionSupport permissionSupport;
    private final KeyValueRpcService keyValueRpcService;
    private final PostContentOutboxService postContentOutboxService;
    private final PostIndexEmitter postIndexEmitter;
    private final BarFeedMqSender barFeedMqSender;

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
    public PostBaseVO createPost(Long userId, CreatePostDTO createPostDTO) {
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
        // 3. 正文经 Outbox 投影到 KV；与 insert 同事务入箱，提交后再写 Cassandra。
        Post post = new Post();
        post.setId(IdUtil.getSnowflakeNextId());
        post.setBarId(createPostDTO.getBarId());
        post.setUserId(userId);
        post.setTitle(createPostDTO.getTitle());
        post.setContent(createPostDTO.getContent());
        post.setContentUuid("");
        post.setContentPreview(Objects.requireNonNullElse(preview(post.getContent()), ""));
        applyCover(post, imageUrls);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        post.setCreateTime(now);
        post.setUpdateTime(now);
        // 显式置 0 而非依赖 DB 默认值：insert 后 MP 不会回读默认值，
        post.setEventStatus(0);
        post.setEmbedStatus(0);
        post.setAnalysisStatus(0);
        post.setAnalysisRetryCount(0);
        postMapper.insert(post);
        long contentOutboxId = postContentOutboxService.enqueueUpsert(post.getId(), post.getContent());
        // 放入布隆过滤器
        postBloomFilter.add(post.getId());
        registerAfterCommit(() -> postContentOutboxService.process(contentOutboxId));
        // 投递给AI模块审核
        registerAfterCommit(() -> {
            barFeedMqSender.send(post.getId(), post.getBarId(), post.getCreateTime());
            aiModerationService.submit(
                    post.getId(), post.getBarId(), post.getUserId(),
                    post.getTitle(), post.getContent(), imageUrls);
        });
        // 操纵postImage数据库
        for (int i = 0; i < objectNames.size(); i++) {
            PostImage postImage = new PostImage();
            postImage.setId(IdUtil.getSnowflakeNextId());
            postImage.setPostId(post.getId());
            postImage.setUrl(imageUrls.get(i));
            postImage.setObjectName(objectNames.get(i));
            postImage.setSortNo(i);
            postImageMapper.insert(postImage);
        }
        refreshBarPostCount(post.getBarId());
        // 直接用手上这份 post 组装，不再重查：时间字段已在 insert 前显式填好。
        // 返回帖子本体：刚发的帖子 AI 还没跑，没有结论可填
        PostBaseVO postBaseVO = new PostBaseVO();
        fillPostFields(postBaseVO, post, bar, imageUrls, post.getContent());
        fillDetailInteraction(postBaseVO);
        return postBaseVO;
    }

    /**
     * 编辑帖子
     * @param operatorId
     * @param postId
     * @param updatePostDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostBaseVO updatePost(Long operatorId, Long postId, UpdatePostDTO updatePostDTO) {
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

        // 要比正文得先从 Cassandra 取回旧值 —— selectById 读出来的 Post.content 是 null
        // KV 读不到时 fetchPostBody 返回 null，按「变了」保守处理：宁可多重审一次，也别漏审。
        boolean textChanged = !Objects.equals(post.getTitle(), updatePostDTO.getTitle());
        if (!textChanged) {
            textChanged = !Objects.equals(fetchPostBody(post), updatePostDTO.getContent());
        }
        long contentOutboxId = postContentOutboxService.enqueueUpsert(postId, updatePostDTO.getContent());
        Post patch = new Post();
        patch.setId(postId);
        patch.setTitle(updatePostDTO.getTitle());
        patch.setContentPreview(Objects.requireNonNullElse(preview(updatePostDTO.getContent()), ""));
        patch.setCoverImage(coverOf(urls));
        patch.setImageCount(urls.size());
        postMapper.updateContent(patch);
        registerAfterCommit(() -> postContentOutboxService.process(contentOutboxId));
        if (textChanged) {
            // 正文一变，旧结论立即作废：状态重置让可见性 SQL 拦下它，
            // 删结论行则保证任何仍按 risk_level 判断的读取方也不会放行新正文。
            postMapper.resetAnalysisOnEdit(postId);
            postAnalysisMapper.deleteByPostId(postId);
        }

        postImageMapper.logicalDeleteByPostId(postId);
        for (int i = 0; i < objectNames.size(); i++) {
            PostImage row = new PostImage();
            row.setId(IdUtil.getSnowflakeNextId());
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
        // 正文一变审核状态就被重置、旧结论行也被删掉 → emit 现算可见性为 false，
        // 索引里那份随之撤掉；没变也要重写，因为封面/配图数可能变了。
        // 无论如何都要发，用提交后手上这份正文省一次 KV 回读
        registerAfterCommit(() -> postIndexEmitter.emit(postId, updatePostDTO.getContent()));
        registerAfterCommit(() -> postDetailCache.evict(postId));
        Post saved = postMapper.selectById(postId);
        if (saved == null) {
            throw new BusinessException("帖子不存在");
        }
        Bar bar = barMapper.selectById(saved.getBarId());
        // 不查 analysis：正文一变审核就被重置，旧结论不该再随响应返回
        // 正文刚从手上这份 DTO 写进去，直接传下去，省一次 KV 回读
        PostBaseVO postBaseVO = new PostBaseVO();
        fillPostFields(postBaseVO, saved, bar, urls, updatePostDTO.getContent());
        fillDetailInteraction(postBaseVO);
        return postBaseVO;
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
        long contentOutboxId = postContentOutboxService.enqueueDelete(postId, post.getContentUuid());
        postMapper.logicalDeleteById(postId);
        postImageMapper.logicalDeleteByPostId(postId);
        refreshBarPostCount(post.getBarId());
        registerAfterCommit(() -> postContentOutboxService.process(contentOutboxId));
        registerAfterCommit(() -> postDetailCache.evict(postId));
        registerAfterCommit(() -> aiModerationService.deleteVector(postId));
        // 从搜索索引里撤掉。正文不用传：行已逻辑删除，emit 只会发一条 visible=false
        registerAfterCommit(() -> postIndexEmitter.emit(postId, null));
    }

    /**
     * 把一段收尾动作挂到事务提交之后执行
     */
    /** 发帖/删帖后按 tb_post 回写 tb_bar.post_count，与展示层 live count 一致 */
    private void refreshBarPostCount(Long barId) {
        if (barId == null) {
            return;
        }
        int rows = barMapper.refreshPostCount(barId);
        if (rows != 1) {
            log.warn("回写吧帖子数未更新到行，barId={}，影响行数={}", barId, rows);
        }
        registerAfterCommit(() -> invalidateBarCaches(barId));
    }

    private void invalidateBarCaches(Long barId) {
        if (barId == null) {
            return;
        }
        try {
            barDetailCache.evict(barId);
            barPageCache.invalidate();
        } catch (Exception e) {
            log.warn("更新吧帖子数后清缓存失败，barId={}", barId, e);
        }
    }

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
     * 查看帖子详情。
     *
     * <p>缓存里保存的是与用户无关的详情。这里在副本上补当前用户的互动状态，
     * 再决定是否暴露作者专属的相似帖 —— 绝不能改到缓存对象。
     */
    @Override
    public PostDetailVO getDetail(Long postId, Long viewerId) {
        PostDetailVO detail = postDetailCache.get(postId, this::loadDetail);
        if (detail == null) {
            throw new BusinessException("帖子不存在");
        }
        PostDetailVO postDetailVO = new PostDetailVO();
        BeanUtils.copyProperties(detail, postDetailVO);
        fillDetailInteraction(postDetailVO);
        fillViewerSimilarPosts(postDetailVO, viewerId);
        return postDetailVO;
    }

    /**
     * 作者轮询审核进度。非作者或帖子不存在一律返回 null，不暴露任何状态。
     */
    @Override
    public PostModerationStatusVO getModerationStatus(Long postId, Long viewerId) {
        Post post = postMapper.selectById(postId);
        if (post == null || viewerId == null || !viewerId.equals(post.getUserId())) {
            return null;
        }
        PostModerationStatusVO vo = new PostModerationStatusVO();
        vo.setPostId(postId);
        vo.setAnalysisStatus(post.getAnalysisStatus());
        vo.setVisible(isPubliclyVisible(postId));
        if (post.getAnalysisStatus() != null && post.getAnalysisStatus() == ANALYSIS_SUCCESS) {
            PostAnalysis analysis = postAnalysisMapper.selectByPostId(postId);
            vo.setRiskLevel(analysis == null ? null : analysis.getRiskLevel());
        }
        return vo;
    }

    /**
     * 帖子存在且对用户可见就返回它，否则 null。详情回源用。
     */
    private Post loadVisiblePost(Long postId) {
        // 一定不存在 → 直接短路，一条 SQL 都不打。这是防缓存穿透的关键一步。
        // 注意顺序：先问过滤器、返回 false 后再看重建标记。重建期间位图刚清空、正在逐条回填，
        // 此时过滤器说的「不存在」不可信，必须继续查库，否则真实帖子会 404。
        if (!postBloomFilter.mightContain(postId) && !postBloomFilterRebuildJob.isRebuilding()) {
            return null;
        }
        // 其余情况都要查库：过滤器返回 true 只代表「可能存在」，也可能是假阳性
        Post post = postMapper.selectById(postId);
        if (post == null || !isPubliclyVisible(postId)) {
            return null;
        }
        return post;
    }

    /**
     * 回源查库（缓存未命中时执行）
     */
    private PostDetailVO loadDetail(Long postId) {
        Post post = loadVisiblePost(postId);
        if (post == null) {
            return null;
        }
        // 图片与贴吧/作者/分析互不依赖：先把图片查询丢进线程池，本线程继续查其余表。
        CompletableFuture<List<String>> imageUrls = loadImageUrlsAsync(post);
        Bar bar = barMapper.selectById(post.getBarId());
        UserBrief author = userReadMapper.selectById(post.getUserId());
        PostAnalysis analysis = loadAnalysisIfSuccess(post);
        return assembleDetail(post, bar, author, joinImageUrls(imageUrls), analysis);
    }

    /**
     * 分页查询帖子
     * @param postFeedQueryDTO
     * @return
     */
    @Override
    public PageVO<PostSummaryVO> feedPosts(PostFeedQueryDTO postFeedQueryDTO) {
        // 而校验注解只覆盖走 HTTP 进来的请求，服务被内部调用时不受它保护
        Integer requestedSize = postFeedQueryDTO.getPageSize();
        int size = requestedSize == null ? PageQueryDTO.DEFAULT_SIZE : Math.min(Math.max(requestedSize, 1), PageQueryDTO.MAX_SIZE);
        Long viewerId = UserContext.getUserId();
        boolean authorSelfView = postFeedQueryDTO.getUserId() != null
                && viewerId != null
                && postFeedQueryDTO.getUserId().equals(viewerId);
        List<PostSummaryVO> records;
        if (postFeedQueryDTO.getLastId() == null) {
            // 首屏走本机缓存。缓存里那份的 liked/favorited 恒为 null，
            String filterKey = PostFeedCache.filterKey(
                    postFeedQueryDTO.getBarId(), postFeedQueryDTO.getUserId(), size, authorSelfView);
            records = postFeedCache.getFirstPage(filterKey,
                    () -> assembleFeedBase(postFeedQueryDTO.getBarId(), postFeedQueryDTO.getUserId(),
                            null, null, size, authorSelfView));
        } else {
            records = assembleFeedBase(postFeedQueryDTO.getBarId(), postFeedQueryDTO.getUserId(),
                    postFeedQueryDTO.getLastId(), postFeedQueryDTO.getLastScore(), size, authorSelfView);
        }
        enrichSummaryRows(records);
        PageVO<PostSummaryVO> result = new PageVO<>();
        //把结果写入返回类
        result.setRecords(records);
        // 写入游标。贴吧流还要带上最后一条的热度，否则下一页无法按热度接着翻
        if (records.size() < size) {
            result.setNextCursor(null);
        } else {
            PostSummaryVO last = records.get(records.size() - 1);
            result.setNextCursor(last.getPostId());
            if (postFeedQueryDTO.getBarId() != null) {
                result.setNextScore(last.getHeatScore() == null ? 0L : last.getHeatScore());
            }
        }
        return result;
    }

    /**
     * 按游标条件取一页并组装成<b>基础 VO</b>——互动字段留空，由调用方填
     */
    private List<PostSummaryVO> assembleFeedBase(Long barId, Long userId, Long lastId, Long lastScore, int size,
                                                 boolean authorSelfView) {
        List<Post> posts = authorSelfView && userId != null
                ? postMapper.selectAuthorFeed(barId, userId, lastId, lastScore, 1, 2, size)
                : postMapper.selectApprovedFeed(barId, userId, lastId, lastScore, 1, 2, size);
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
    public Map<Long, PostSummaryVO> latestVisibleByBarIds(List<Long> barIds) {
        if (barIds == null || barIds.isEmpty()) {
            return Map.of();
        }
        List<Post> posts = postMapper.selectLatestApprovedByBarIds(barIds);
        if (posts.isEmpty()) {
            return Map.of();
        }
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(userIds.stream().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, PostSummaryVO> result = new HashMap<>();
        for (Post post : posts) {
            result.put(post.getBarId(), toSummaryVO(post, null, userById.get(post.getUserId())));
        }
        enrichSummaryRows(new ArrayList<>(result.values()));
        return result;
    }

    @Override
    public List<PostSummaryVO> listRecentVisible(int limit) {
        int size = limit <= 0 ? PageQueryDTO.DEFAULT_SIZE : Math.min(limit, PageQueryDTO.MAX_SIZE);
        List<Post> posts = postMapper.selectApprovedRecent(size);
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(userIds.stream().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectByIds(barIds.stream().toList()).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));
        List<PostSummaryVO> records = posts.stream()
                .map(post -> toSummaryVO(post,
                        barById.get(post.getBarId()),
                        userById.get(post.getUserId())))
                .collect(Collectors.toCollection(ArrayList::new));
        enrichSummaryRows(records);
        return records;
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
        // 与 feedPosts 同样的批量取关联，再加一次分析结论，避免逐帖回源
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());

        Map<Long, List<PostImage>> imagesByPostId = listImagesByPostIds(postIds);
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(userIds.stream().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectByIds(barIds.stream().toList()).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));
        Map<Long, PostAnalysis> analysisByPostId = listAnalysisByPostIds(postIds);

        int warmed = 0;
        for (Post post : posts) {
            String body = fetchPostBody(post);
            if (body == null) {
                // 正文没取到：跳过这一条，别把摘要当正文缓存住整个 TTL。
                // 按需回源时还会再试一次，单条失败也不影响整轮预热。
                continue;
            }
            List<PostImage> images = imagesByPostId.getOrDefault(post.getId(), List.of());
            postDetailCache.put(assembleDetail(post,
                    barById.get(post.getBarId()),
                    userById.get(post.getUserId()),
                    images.stream().map(PostImage::getUrl).toList(),
                    analysisByPostId.get(post.getId()),
                    body));
            warmed++;
        }
        return warmed;
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
        return assembleDetail(post, bar, author, imageUrls, analysis, loadPostBody(post));
    }

    /** 重载：正文已经知道时（刚写完 / 预热时已取过）直接传进来，省一次 KV 回读 */
    private PostDetailVO assembleDetail(Post post, Bar bar, UserBrief author, List<String> imageUrls,
                                       PostAnalysis analysis, String content) {
        PostDetailVO postDetailVO = new PostDetailVO();
        fillPostFields(postDetailVO, post, bar, imageUrls, content);
        postDetailVO.setAnalysisStatus(post.getAnalysisStatus());
        fillAnalysis(postDetailVO, analysis);
        return postDetailVO;
    }

    /**
     * 填帖子本体字段（不含 AI 结论）。发帖 / 改帖的返回与详情共用这一段。
     *
     * @param content 正文。调用方要么刚从手上写进去，要么已从 KV 取过，避免这里再回读一次
     */
    private void fillPostFields(PostBaseVO vo, Post post, Bar bar, List<String> imageUrls, String content) {
        vo.setPostId(post.getId());
        vo.setBarId(post.getBarId());
        vo.setBarName(bar == null ? null : bar.getName());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(content);
        vo.setImages(imageUrls);
        vo.setCreateTime(post.getCreateTime());
        vo.setUpdateTime(post.getUpdateTime());
    }

    /**
     * 公开可见：AI 判定为「无风险」且 analysis_status 仍为成功。
     * 判定下沉在 SQL 里，见 PostAnalysisMapper.xml 的 countPubliclyVisible。
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

    /**
     * 相似帖是作者专属数据。缓存里可以带 similarPostIds，
     * 但只能在返回副本上决定是否暴露，非作者显式抹掉。
     */
    private void fillViewerSimilarPosts(PostDetailVO vo, Long viewerId) {
        boolean isAuthor = viewerId != null && viewerId.equals(vo.getUserId());
        vo.setViewerIsAuthor(isAuthor);
        if (!isAuthor) {
            vo.setSimilarPostIds(null);
            vo.setSimilarPosts(null);
            return;
        }
        vo.setSimilarPosts(loadSimilarPostSummaries(vo.getPostId(), vo.getSimilarPostIds()));
    }

    /**
     * 按 AI 排名顺序批量组装相似帖摘要。
     * 只返回当前仍公开可见的帖子；已删除/重新审核/被拒的相似帖在读取时过滤掉。
     */
    private List<PostSummaryVO> loadSimilarPostSummaries(Long currentPostId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, currentPostId))
                .distinct()
                .limit(MAX_SIMILAR_POSTS)
                .toList();
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        List<Post> posts = postMapper.selectApprovedByIds(orderedIds);
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> postById = posts.stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, UserBrief> userById = userReadMapper.selectByIds(
                        posts.stream().map(Post::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(UserBrief::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectByIds(
                        posts.stream().map(Post::getBarId).distinct().toList()).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));

        List<PostSummaryVO> rows = new ArrayList<>();
        // 按 orderedIds 遍历而不是 SQL 返回顺序，保证 AI 排名
        for (Long id : orderedIds) {
            Post post = postById.get(id);
            if (post != null) {
                rows.add(toSummaryVO(post, barById.get(post.getBarId()), userById.get(post.getUserId())));
            }
        }
        fillSummaryInteraction(rows);
        return rows;
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
        // 摘要直接用库里存的那份。别再 preview(content) —— content 不是数据库列，读出来是 null
        vo.setContentPreview(post.getContentPreview());
        vo.setCoverImage(post.getCoverImage());
        vo.setImageCount(post.getImageCount() == null ? 0 : post.getImageCount());
        vo.setCreateTime(post.getCreateTime());
        vo.setHeatScore(post.getHeatScore());
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
    /**
     * 从 Cassandra 取正文，取不到返回 null（不回退摘要）。
     *
     * <p>回退链的第一段：优先用库里存的 {@code content_uuid}；为空则用帖子 id 推导
     * （{@code new UUID(0, postId)}）—— 迁移前的老行 uuid 是空串，但正文其实已经在
     * Cassandra 里，主键就是这个推导值，这一步把老数据救回来。
     */
    private String fetchPostBody(Post post) {
        return post == null ? null : keyValueRpcService.findPostBody(post.getId(), post.getContentUuid());
    }

    /**
     * 详情页用的正文：Cassandra 取不到就回退到 {@code content_preview}。
     * KV 抖一下最多少看正文，不让详情页 500 —— 摘要总还是有的。
     */
    private String loadPostBody(Post post) {
        String body = fetchPostBody(post);
        return StringUtils.hasText(body) ? body : (post == null ? null : post.getContentPreview());
    }

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
    private void fillDetailInteraction(PostBaseVO postDetailVO) {
        Long postId = postDetailVO.getPostId();
        List<Long> ids = List.of(postId);
        Long userId = UserContext.getUserId();
        postDetailVO.setLikeCount(postEngagementSupport.getLikeCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setCommentCount(postEngagementSupport.getCommentCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setFavoriteCount(postEngagementSupport.getFavoriteCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setLiked(userId != null && postEngagementSupport.listLikedPostIds(userId, ids).contains(postId));
        postDetailVO.setFavorited(userId != null && postEngagementSupport.listFavoritedPostIds(userId, ids).contains(postId));
    }

    /** 列表行：帖子互动 + 所属吧粉丝数/帖子数/关注状态（用户维度，不进 feed 缓存） */
    private void enrichSummaryRows(List<PostSummaryVO> rows) {
        fillSummaryInteraction(rows);
        fillSummaryBarContext(rows);
    }

    private void fillSummaryBarContext(List<PostSummaryVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> barIds = rows.stream()
                .map(PostSummaryVO::getBarId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (barIds.isEmpty()) {
            return;
        }
        Map<Long, Bar> barById = barMapper.selectByIds(barIds).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));
        Map<Long, Long> postCountByBarId = postMapper.countNotDeletedByBarIds(barIds).stream()
                .collect(Collectors.toMap(BarPostCountRow::getBarId, BarPostCountRow::getPostCount, (a, b) -> a));
        Long viewerId = UserContext.getUserId();
        Set<Long> followedBarIds = Set.of();
        if (viewerId != null) {
            List<Long> followed = barFollowReadMapper.selectFollowedBarIds(viewerId, barIds);
            followedBarIds = followed == null ? Set.of() : new HashSet<>(followed);
        }
        for (PostSummaryVO row : rows) {
            Long barId = row.getBarId();
            Bar bar = barById.get(barId);
            if (bar != null) {
                if (row.getBarName() == null) {
                    row.setBarName(bar.getName());
                }
                row.setBarFollowCount(bar.getFollowCount() == null ? 0 : bar.getFollowCount());
            } else {
                row.setBarFollowCount(0);
            }
            int livePostCount = postCountByBarId.getOrDefault(barId, 0L).intValue();
            row.setBarPostCount(livePostCount);
            row.setBarFollowed(viewerId != null && barId != null && followedBarIds.contains(barId));
        }
    }

    //查询评论数，点赞数和收藏数
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
