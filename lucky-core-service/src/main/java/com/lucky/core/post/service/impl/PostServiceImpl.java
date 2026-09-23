package com.lucky.core.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lucky.bloom.LongBloomFilter;
import com.lucky.core.bar.entity.Bar;
import com.lucky.core.bar.entity.BarManager;
import com.lucky.core.bar.mapper.BarManagerMapper;
import com.lucky.core.bar.mapper.BarMapper;
import com.lucky.core.post.cache.PostDetailCache;
import com.lucky.core.post.cache.PostFeedCache;
import com.lucky.core.post.dto.CreatePostDTO;
import com.lucky.core.post.dto.PostFeedQueryDTO;
import com.lucky.core.post.dto.PostQueryDTO;
import com.lucky.core.post.dto.UpdatePostDTO;
import com.lucky.core.post.entity.Post;
import com.lucky.core.post.entity.PostAnalysis;
import com.lucky.core.post.entity.PostImage;
import com.lucky.core.user.entity.User;
import com.lucky.core.post.mapper.PostAnalysisMapper;
import com.lucky.core.post.mapper.PostImageMapper;
import com.lucky.core.post.mapper.PostMapper;
import com.lucky.core.user.mapper.UserMapper;
import com.lucky.core.user.service.PermissionService;
import com.lucky.core.ai.AiModerationService;
import com.lucky.core.ai.vo.EvidenceVO;
import com.lucky.core.comment.dto.CommentQueryDTO;
import com.lucky.core.comment.service.CommentService;
import com.lucky.core.favorite.service.FavoriteService;
import com.lucky.core.file.FileService;
import com.lucky.core.post.service.PostService;
import com.lucky.core.post.vo.PageVO;
import com.lucky.core.post.vo.PostDetailVO;
import com.lucky.core.post.vo.PostSummaryVO;
import com.lucky.core.post.vo.PostViewVO;
import com.lucky.core.comment.vo.CommentVO;
import com.lucky.core.thumb.service.ThumbService;
import com.lucky.core.auth.UserContext;
import com.lucky.common.constant.PostConstant;
import com.lucky.common.constant.CommentConstant;
import com.lucky.common.dto.PageQueryDTO;
import com.lucky.common.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
public class PostServiceImpl implements PostService {

    /** 与 tb_post.analysis_status 列注释一致：2 = 分析成功 */
    private static final int ANALYSIS_SUCCESS = 2;

    /**
     * 解析 evidence_json。刻意不注入容器里的 ObjectMapper：那边是缓存二进制格式，
     * 语义混用；EvidenceVO 没有时间字段，裸 JsonMapper 足够。
     */
    private static final ObjectMapper ANALYSIS_JSON = JsonMapper.builder().build();

    @Resource
    private PostMapper postMapper;

    @Resource
    private PostImageMapper postImageMapper;

    @Resource
    private PostAnalysisMapper postAnalysisMapper;

    @Resource
    private BarMapper barMapper;

    @Resource
    private BarManagerMapper barManagerMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FileService fileService;

    @Resource
    private PostDetailCache postDetailCache;

    @Resource
    private PostFeedCache postFeedCache;

    @Resource
    private LongBloomFilter postBloomFilter;

    @Resource
    private AiModerationService aiModerationService;

    @Resource
    private ThumbService thumbService;

    @Resource
    private CommentService commentService;

    @Resource
    private FavoriteService favoriteService;

    @Resource
    private PermissionService permissionService;

    @Resource(name = "postImageQueryExecutor")
    private ThreadPoolTaskExecutor postImageQueryExecutor;

    /** 需要手动取「当前时刻」时统一走它，见 {@link com.lucky.core.config.ClockConfig} */
    @Resource
    private Clock clock;

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
        postMapper.insert(post);   // MP 回填雪花ID
        // 放入布隆过滤器
        postBloomFilter.add(post.getId());
        registerAfterCommit(() -> aiModerationService.submit(post.getId(), post.getTitle(), post.getContent()));
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
        LambdaUpdateWrapper<Post> updatePost = new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .set(Post::getTitle, updatePostDTO.getTitle())
                .set(Post::getContent, updatePostDTO.getContent())
                .set(Post::getCoverImage, coverOf(urls))
                .set(Post::getImageCount, urls.size());
        if (textChanged) {
            updatePost.set(Post::getAnalysisStatus, 0)
                    .set(Post::getEventStatus, 0)
                    .set(Post::getEmbedStatus, 0)
                    .set(Post::getAnalysisRetryCount, 0)
                    .set(Post::getAnalysisTime, null);
        }
        postMapper.update(null, updatePost);

        postImageMapper.delete(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, postId));
        for (int i = 0; i < objectNames.size(); i++) {
            PostImage row = new PostImage();
            row.setPostId(postId);
            row.setUrl(urls.get(i));
            row.setObjectName(objectNames.get(i));
            row.setSortNo(i);
            postImageMapper.insert(row);
        }
        if (textChanged) {
            registerAfterCommit(() -> aiModerationService.submit(postId, updatePostDTO.getTitle(), updatePostDTO.getContent()));
        }
        registerAfterCommit(() -> postDetailCache.evict(postId));
        Post saved = postMapper.selectById(postId);
        if (saved == null) {
            throw new BusinessException("帖子不存在");
        }
        Bar bar = barMapper.selectById(saved.getBarId());
        User author = userMapper.selectById(saved.getUserId());
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
        postMapper.deleteById(postId);
        postImageMapper.delete(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, postId));
        registerAfterCommit(() -> postDetailCache.evict(postId));
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
        CommentQueryDTO query = new CommentQueryDTO();
        query.setPostId(postId);
        query.setPageNum(1);
        query.setPageSize(CommentConstant.FIRST_PAGE_SIZE);
        IPage<CommentVO> comments = commentService.pageComments(UserContext.getUserId(), query);
        PostViewVO postViewVO = new PostViewVO();
        postViewVO.setPost(post);
        postViewVO.setComments(comments.getRecords());
        postViewVO.setRootCommentTotal(comments.getTotal());
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
        if (post == null) {
            return null;
        }
        // 图片与贴吧/作者/分析互不依赖：先把图片查询丢进线程池，本线程继续查其余表。
        CompletableFuture<List<String>> imageUrls = loadImageUrlsAsync(post);
        Bar bar = barMapper.selectById(post.getBarId());
        User author = userMapper.selectById(post.getUserId());
        PostAnalysis analysis = loadAnalysisIfSuccess(post);
        return assembleDetail(post, bar, author, joinImageUrls(imageUrls), analysis);
    }

    @Override
    public IPage<PostSummaryVO> pagePosts(PostQueryDTO query) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getBarId() != null, Post::getBarId, query.getBarId())
                .eq(query.getUserId() != null, Post::getUserId, query.getUserId())
                // 必须显式排序：不写 ORDER BY 时 PostgreSQL 行序不保证，翻页会重复或漏行。
                .orderByDesc(Post::getId);
        Integer pageNum= query.getPageNum();
        Integer pageSize= query.getPageSize();
        Page<Post> page=new Page<>(pageNum, pageSize, false);
        page = postMapper.selectPage(page, wrapper);
        Page<PostSummaryVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (page.getRecords().isEmpty()) {
            // 本页无数据时跳过后面的贴吧 / 作者批量查询
            return result;
        }
        // 封面与张数已在 tb_post，列表不再回查图片表；先去重再批量查贴吧和作者
        Set<Long> userIds = page.getRecords().stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = page.getRecords().stream().map(Post::getBarId).collect(Collectors.toSet());

        Map<Long, User> userById = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectBatchIds(barIds).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));

        result.setRecords(page.getRecords().stream()
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
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(barId != null, Post::getBarId, barId)
                .eq(userId != null, Post::getUserId, userId)
                // 游标条件。size 是上面夹取过的 int，不存在注入面（与 warmUpRecentPosts 同一处理）
                .lt(lastId != null, Post::getId, lastId)
                .orderByDesc(Post::getId)
                .last("LIMIT " + size);
        List<Post> posts = postMapper.selectList(wrapper);
        if (posts.isEmpty()) {
            return List.of();
        }

        // 封面/张数已冗余在 tb_post，列表不再回查图片表；去重后只批量查贴吧和作者
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());

        Map<Long, User> userById = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectBatchIds(barIds).stream()
                .collect(Collectors.toMap(Bar::getId, Function.identity()));

        // 用 toCollection(ArrayList::new) 而不是 toList()：结果会进缓存并被拿去做响应体，
        // 要一份确实可变的列表，别依赖 toList() 的实现细节
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
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .orderByDesc(Post::getId)
                .last("LIMIT " + safeLimit));
        if (posts.isEmpty()) {
            return 0;
        }
        // 与 pagePosts 同样的批量取关联，再加一次分析结论，避免逐帖回源
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Set<Long> barIds = posts.stream().map(Post::getBarId).collect(Collectors.toSet());

        Map<Long, List<PostImage>> imagesByPostId = listImagesByPostIds(postIds);
        Map<Long, User> userById = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Bar> barById = barMapper.selectBatchIds(barIds).stream()
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
        if (permissionService.isPlatformAdmin(userId)) {
            return true;
        }
        Bar bar = barMapper.selectById(barId);
        if (bar != null && userId.equals(bar.getOwnerUserId())) {
            return true;
        }
        return barManagerMapper.selectCount(new LambdaQueryWrapper<BarManager>()
                .eq(BarManager::getBarId, barId)
                .eq(BarManager::getUserId, userId)
                .eq(BarManager::getStatus, BarManager.STATUS_ACTIVE)) > 0;
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
        return postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                        .in(PostImage::getPostId, postIds)
                        .orderByAsc(PostImage::getSortNo))
                .stream()
                .collect(Collectors.groupingBy(PostImage::getPostId));
    }

    private PostDetailVO assembleDetail(Post post, Bar bar, User author, List<String> imageUrls,
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

    private PostAnalysis loadAnalysisIfSuccess(Post post) {
        if (post.getAnalysisStatus() == null || post.getAnalysisStatus() != ANALYSIS_SUCCESS) {
            return null;
        }
        return postAnalysisMapper.selectOne(new LambdaQueryWrapper<PostAnalysis>()
                .eq(PostAnalysis::getPostId, post.getId())
                .last("LIMIT 1"));
    }

    private Map<Long, PostAnalysis> listAnalysisByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return postAnalysisMapper.selectList(new LambdaQueryWrapper<PostAnalysis>()
                        .in(PostAnalysis::getPostId, postIds))
                .stream()
                .collect(Collectors.toMap(PostAnalysis::getPostId, Function.identity(), (a, b) -> a));
    }

    private void fillAnalysis(PostDetailVO vo, PostAnalysis analysis) {
        if (analysis == null) {
            return;
        }
        vo.setAnalysisRiskLevel(analysis.getRiskLevel());
        vo.setAnalysisConfidence(analysis.getConfidence() == null ? null : analysis.getConfidence().doubleValue());
        vo.setAnalysisReasoning(analysis.getReasoning());
        if (analysis.getRiskCategories() == null || analysis.getRiskCategories().isBlank()) {
            vo.setAnalysisCategories(List.of());
        } else {
            vo.setAnalysisCategories(List.of(analysis.getRiskCategories().split(",")));
        }
        vo.setAnalysisEvidence(parseEvidence(analysis.getEvidenceJson()));
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

    private PostSummaryVO toSummaryVO(Post post, Bar bar, User author) {
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
        postDetailVO.setLikeCount(thumbService.getLikeCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setCommentCount(commentService.getCommentCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setFavoriteCount(favoriteService.getFavoriteCounts(ids).getOrDefault(postId, 0L));
        postDetailVO.setLiked(userId != null && thumbService.listLikedPostIds(userId, ids).contains(postId));
        postDetailVO.setFavorited(userId != null && favoriteService.listFavoritedPostIds(userId, ids).contains(postId));
    }

    private void fillSummaryInteraction(List<PostSummaryVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(PostSummaryVO::getPostId).toList();
        Long userId = UserContext.getUserId();
        Map<Long, Long> likes = thumbService.getLikeCounts(ids);
        Map<Long, Long> comments = commentService.getCommentCounts(ids);
        Map<Long, Long> favorites = favoriteService.getFavoriteCounts(ids);
        Set<Long> liked = userId == null ? Set.of() : thumbService.listLikedPostIds(userId, ids);
        Set<Long> favorited = userId == null ? Set.of() : favoriteService.listFavoritedPostIds(userId, ids);
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
