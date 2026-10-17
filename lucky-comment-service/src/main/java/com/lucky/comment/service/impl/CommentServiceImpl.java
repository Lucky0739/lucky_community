package com.lucky.comment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.google.common.util.concurrent.RateLimiter;
import com.lucky.comment.cache.CommentCacheCoordinator;
import com.lucky.comment.cache.CommentCountCache;
import com.lucky.comment.cache.CommentLikeCache;
import com.lucky.comment.cache.CommentListCache;
import com.lucky.comment.cache.CommentReplyListCache;
import com.lucky.comment.constant.CommentLikeMqConstant;
import com.lucky.comment.constant.CommentMqConstant;
import com.lucky.comment.dto.FindCommentPageListDTO;
import com.lucky.comment.dto.FindCommentReplyPageListDTO;
import com.lucky.comment.dto.PublishCommentDTO;
import com.lucky.comment.entity.Comment;
import com.lucky.comment.entity.PostRef;
import com.lucky.comment.enums.CommentLevelEnum;
import com.lucky.comment.mapper.CommentLikeMapper;
import com.lucky.comment.mapper.CommentMapper;
import com.lucky.comment.mapper.PostCountMapper;
import com.lucky.comment.mapper.PostReadMapper;
import com.lucky.comment.mq.CommentMqSender;
import com.lucky.comment.mq.message.CommentLikeMessage;
import com.lucky.comment.mq.message.DeleteCommentMessage;
import com.lucky.comment.mq.message.PublishCommentMessage;
import com.lucky.comment.rpc.KeyValueRpcService;
import com.lucky.comment.rpc.UserRpcService;
import com.lucky.comment.service.CommentService;
import com.lucky.comment.vo.FindCommentPageListVO;
import com.lucky.common.auth.UserContext;
import com.lucky.common.constant.CommentConstant;
import com.lucky.common.exception.BusinessException;
import com.lucky.common.vo.PageResult;
import com.lucky.kv.api.dto.FindCommentContentDTO;
import com.lucky.kv.api.vo.CommentContentItemVO;
import com.lucky.user.api.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    /** 评论列表每页条数。DTO 没带 pageSize，用固定值 */
    private static final int PAGE_SIZE = CommentConstant.FIRST_PAGE_SIZE;

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 级联删回复时每批处理多少条 */
    private static final int CASCADE_CHUNK = 200;

    /** 删 Cassandra 正文的限速 */
    private static final double CONTENT_DELETE_QPS = 200;

    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final PostCountMapper postCountMapper;
    private final PostReadMapper postReadMapper;
    private final KeyValueRpcService keyValueRpcService;
    private final UserRpcService userRpcService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ThreadPoolTaskExecutor commentCacheExecutor;
    private final CommentListCache commentListCache;
    private final CommentReplyListCache commentReplyListCache;
    private final CommentLikeCache commentLikeCache;
    private final CommentCountCache commentCountCache;
    private final CommentCacheCoordinator commentCacheCoordinator;
    private final CommentMqSender commentMqSender;
    private final RocketMQTemplate rocketMQTemplate;
    private final Clock clock;

    private final RateLimiter contentDeleteLimiter = RateLimiter.create(CONTENT_DELETE_QPS);

    @Override
    public PageResult<FindCommentPageListVO> findCommentPageList(FindCommentPageListDTO findCommentPageListDTO) {
        return findCommentPageList(findCommentPageListDTO, null);
    }

    @Override
    public PageResult<FindCommentPageListVO> findCommentPageList(FindCommentPageListDTO findCommentPageListDTO,
                                                                 Long viewerUserId) {
        Long postId = findCommentPageListDTO.getPostId();
        int pageNo = findCommentPageListDTO.getPageNo() == null ? 1 : findCommentPageListDTO.getPageNo();
        long offset = (long) (pageNo - 1) * PAGE_SIZE;

        long total = loadCommentTotal(postId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, pageNo, PAGE_SIZE);
        }

        // 先在缓存里找：ZSet 按发布时间升序取这一页 ID → MGET 详情
        List<FindCommentPageListVO> records = null;
        if (offset < CommentListCache.MAX_CACHED) {
            records = loadFromCache(postId, offset, total, viewerUserId);
        }
        // 缓存没覆盖 → 回源数据库，并异步回填缓存
        if (records == null) {
            List<Comment> oneLevel = commentMapper.selectPageList(postId, offset, PAGE_SIZE);
            records = buildVOs(postId, oneLevel, viewerUserId);
            cachePageAsync(postId, oneLevel, records);
            if (!commentListCache.exists(postId)) {
                refillListCacheAsync(postId);
            }
        }
        return decorateLikeState(records, viewerUserId, total, pageNo, PAGE_SIZE);
    }

    @Override
    public PageResult<FindCommentPageListVO> findReplyPageList(FindCommentReplyPageListDTO dto, Long viewerUserId) {
        Long postId = dto.getPostId();
        Long rootCommentId = dto.getRootCommentId();
        int pageNo = dto.getPageNo() == null ? 1 : dto.getPageNo();
        long offset = (long) (pageNo - 1) * PAGE_SIZE;

        long total = loadReplyTotal(rootCommentId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, pageNo, PAGE_SIZE);
        }

        List<FindCommentPageListVO> records = null;
        if (offset < CommentReplyListCache.MAX_CACHED) {
            records = loadRepliesFromCache(postId, rootCommentId, offset, total, viewerUserId);
        }
        if (records == null) {
            List<Comment> replies = commentMapper.selectReplyPageList(rootCommentId, offset, PAGE_SIZE);
            records = buildVOs(postId, replies, viewerUserId);
            Long rootId = rootCommentId;
            commentCacheExecutor.execute(() -> commentReplyListCache.syncIds(rootId, replies));
            List<FindCommentPageListVO> voCopy = List.copyOf(records);
            commentCacheExecutor.execute(() -> commentListCache.syncVOs(voCopy));
            if (!commentReplyListCache.exists(rootId)) {
                commentCacheExecutor.execute(() -> commentReplyListCache.syncIds(rootId,
                        commentMapper.selectReplyPageList(rootId, 0, CommentReplyListCache.MAX_CACHED)));
            }
        }
        return decorateLikeState(records, viewerUserId, total, pageNo, PAGE_SIZE);
    }

    private PageResult<FindCommentPageListVO> decorateLikeState(List<FindCommentPageListVO> records,
                                                                Long viewerUserId,
                                                                long total, int pageNo, int pageSize) {
        if (viewerUserId != null && records != null) {
            for (FindCommentPageListVO vo : records) {
                vo.setLiked(commentLikeCache.isLiked(viewerUserId, vo.getCommentId()));
                Long cachedCount = commentLikeCache.readCount(vo.getCommentId());
                if (cachedCount != null) {
                    vo.setLikeCount(cachedCount);
                }
                if (vo.getFirstReplyComment() != null) {
                    vo.getFirstReplyComment().setLiked(
                            commentLikeCache.isLiked(viewerUserId, vo.getFirstReplyComment().getCommentId()));
                }
            }
        }
        return new PageResult<>(records, total, pageNo, pageSize);
    }

    private List<FindCommentPageListVO> loadRepliesFromCache(Long postId, Long rootCommentId, long offset,
                                                             long total, Long viewerUserId) {
        long expected = Math.min(PAGE_SIZE, total - offset);
        if (expected <= 0) {
            return List.of();
        }
        List<Long> ids = commentReplyListCache.rangeReplyIds(rootCommentId, offset, expected);
        if (ids.size() < expected) {
            return null;
        }
        Map<Long, FindCommentPageListVO> merged = new HashMap<>(commentListCache.mgetVOs(ids));
        List<Long> missIds = ids.stream().filter(id -> !merged.containsKey(id)).toList();
        if (!missIds.isEmpty()) {
            List<Comment> missed = commentMapper.selectByIds(missIds);
            List<FindCommentPageListVO> missedVOs = buildVOs(postId, missed, viewerUserId);
            missedVOs.forEach(vo -> merged.put(vo.getCommentId(), vo));
            commentCacheExecutor.execute(() -> commentListCache.syncVOs(missedVOs));
        }
        return ids.stream().map(merged::get).filter(Objects::nonNull).toList();
    }

    private long loadReplyTotal(Long rootCommentId) {
        Long cached = commentCountCache.readReplyTotal(rootCommentId);
        if (cached != null) {
            return cached;
        }
        long dbCount = commentMapper.countRepliesByRoot(rootCommentId);
        commentCacheExecutor.execute(() -> commentCountCache.backfillReplyTotal(rootCommentId, dbCount));
        return dbCount;
    }

    /**
     * 从缓存取一页评论。缓存没覆盖这一页（ZSet 条数不足）时返回 null，交给调用方回源。
     * 详情已过期的个别评论，查库补齐并异步写回。
     */
    private List<FindCommentPageListVO> loadFromCache(Long postId, long offset, long total, Long viewerUserId) {
        long expected = Math.min(PAGE_SIZE, total - offset);       // 这一页应该有这么多条
        if (expected <= 0) {
            return List.of();
        }
        List<Long> ids = commentListCache.rangeCommentIds(postId, offset, expected);
        if (ids.size() < expected) {
            return null;
        }
        Map<Long, FindCommentPageListVO> merged = new HashMap<>(commentListCache.mgetVOs(ids));
        List<Long> missIds = ids.stream().filter(id -> !merged.containsKey(id)).toList();
        if (!missIds.isEmpty()) {
            List<Comment> missed = commentMapper.selectByIds(missIds);
            List<FindCommentPageListVO> missedVOs = buildVOs(postId, missed, viewerUserId);
            missedVOs.forEach(vo -> merged.put(vo.getCommentId(), vo));
            commentCacheExecutor.execute(() -> commentListCache.syncVOs(missedVOs));
        }
        return ids.stream().map(merged::get).filter(Objects::nonNull).toList();
    }

    /** 把这页一级评论写进缓存：ZSet 补 ID，详情写 VO */
    private void cachePageAsync(Long postId, List<Comment> oneLevel, List<FindCommentPageListVO> records) {
        if (oneLevel.isEmpty()) {
            return;
        }
        List<Comment> commentCopy = List.copyOf(oneLevel);
        List<FindCommentPageListVO> voCopy = List.copyOf(records);
        commentCacheExecutor.execute(() -> {
            commentListCache.syncIds(postId, commentCopy);
            commentListCache.syncVOs(voCopy);
        });
    }

    /** 异步回填列表 ZSet：一次取最早的 MAX_CACHED 条一级评论的 ID */
    private void refillListCacheAsync(Long postId) {
        commentCacheExecutor.execute(() ->
                commentListCache.syncIds(postId,
                        commentMapper.selectPageList(postId, 0, CommentListCache.MAX_CACHED)));
    }

    /** 组装这一批一级评论的 VO：带首条二级回复、正文和作者 */
    private List<FindCommentPageListVO> buildVOs(Long postId, List<Comment> oneLevel, Long viewerUserId) {
        if (oneLevel.isEmpty()) {
            return List.of();
        }
        // 每个一级评论只留第一条回复做折叠预览
        List<Long> parentIds = oneLevel.stream().map(Comment::getId).toList();
        Map<Long, Comment> firstReplyByParent = new HashMap<>();
        for (Comment reply : commentMapper.selectTwoLevelCommentByIds(parentIds)) {
            firstReplyByParent.putIfAbsent(reply.getParentId(), reply);
        }

        // 一级评论 + 各自首条回复，一次性取回正文和作者
        List<Comment> rendered = new ArrayList<>(oneLevel);
        rendered.addAll(firstReplyByParent.values());
        Map<String, String> contentByUuid = loadContents(postId, rendered);
        Map<Long, UserBriefVO> userById = loadUsers(rendered);

        List<FindCommentPageListVO> records = new ArrayList<>(oneLevel.size());
        for (Comment one : oneLevel) {
            FindCommentPageListVO vo = toVO(one, contentByUuid, userById, viewerUserId);
            Comment firstReply = firstReplyByParent.get(one.getId());
            if (firstReply != null) {
                vo.setFirstReplyComment(toVO(firstReply, contentByUuid, userById, viewerUserId));
            }
            records.add(vo);
        }
        return records;
    }

    /**
     * 取分页总数（一级评论数）。先读 Redis Hash {@code comment:total:{postId}} 的 {@code commentTotal}；
     * 未命中回源 count(*) 并异步回填。回填带随机 TTL（1 小时 + 0~4 小时），
     * 且写入不刷新 TTL —— 所以 key 过期后计数会从库重算，漂了能自愈。
     */
    private long loadCommentTotal(Long postId) {
        Long cached = commentCountCache.readCommentTotal(postId);
        if (cached != null) {
            return cached;
        }
        long dbCount = commentMapper.countRootComments(postId);
        commentCacheExecutor.execute(() -> commentCountCache.backfillCommentTotal(postId, dbCount));
        return dbCount;
    }

    /** 批量取正文。只有带正文的评论才拼 key，正文存 Cassandra，按 yyyy-MM 分表 */
    private Map<String, String> loadContents(Long postId, List<Comment> comments) {
        List<FindCommentContentDTO> keys = new ArrayList<>();
        for (Comment comment : comments) {
            String uuid = comment.getContentUuid();
            if (uuid != null && !uuid.isBlank() && comment.getCreateTime() != null) {
                keys.add(FindCommentContentDTO.builder()
                        .yearMonth(comment.getCreateTime().format(YEAR_MONTH))
                        .contentId(uuid)
                        .build());
            }
        }
        List<CommentContentItemVO> contents = keyValueRpcService.batchFindCommentContent(postId, keys);
        Map<String, String> byUuid = new HashMap<>();
        if (contents != null) {
            for (CommentContentItemVO item : contents) {
                byUuid.putIfAbsent(item.getContentId(), item.getContent());
            }
        }
        return byUuid;
    }

    /** 批量取作者昵称和头像 */
    private Map<Long, UserBriefVO> loadUsers(List<Comment> comments) {
        Set<Long> userIds = new HashSet<>();
        for (Comment comment : comments) {
            if (comment.getUserId() != null) {
                userIds.add(comment.getUserId());
            }
            Long replyUserId = comment.getReplyUserId();
            if (replyUserId != null && replyUserId > CommentConstant.NONE) {
                userIds.add(replyUserId);
            }
        }
        List<UserBriefVO> users = userRpcService.findByIds(new ArrayList<>(userIds));
        Map<Long, UserBriefVO> byUserId = new HashMap<>();
        if (users != null) {
            for (UserBriefVO user : users) {
                byUserId.putIfAbsent(user.getUserId(), user);
            }
        }
        return byUserId;
    }

    private FindCommentPageListVO toVO(Comment comment, Map<String, String> contentByUuid,
                                       Map<Long, UserBriefVO> userById, Long viewerUserId) {
        FindCommentPageListVO vo = new FindCommentPageListVO();
        vo.setCommentId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setUserId(comment.getUserId());
        UserBriefVO author = userById.get(comment.getUserId());
        if (author != null) {
            vo.setNickName(author.getNickName());
            vo.setIcon(author.getIcon());
        }
        vo.setLevel(comment.getLevel());
        vo.setParentId(comment.getParentId());
        boolean isRoot = CommentLevelEnum.ONE.getCode().equals(comment.getLevel());
        vo.setRootId(isRoot ? CommentConstant.NONE : comment.getParentId());
        vo.setReplyCommentId(comment.getReplyCommentId());
        vo.setReplyUserId(comment.getReplyUserId());
        if (comment.getReplyUserId() != null && comment.getReplyUserId() > CommentConstant.NONE) {
            UserBriefVO replyUser = userById.get(comment.getReplyUserId());
            vo.setReplyNickName(replyUser == null ? null : replyUser.getNickName());
        }
        String uuid = comment.getContentUuid();
        vo.setContent(uuid == null || uuid.isBlank() ? "" : contentByUuid.getOrDefault(uuid, ""));
        vo.setImageUrl(comment.getImageUrl() == null || comment.getImageUrl().isBlank() ? null : comment.getImageUrl());
        vo.setReplyCount(comment.getReplyTotal());
        Long cachedLike = commentLikeCache.readCount(comment.getId());
        vo.setLikeCount(cachedLike != null ? cachedLike : comment.getLikeTotal());
        vo.setLiked(viewerUserId != null && commentLikeCache.isLiked(viewerUserId, comment.getId()));
        vo.setTop(Integer.valueOf(CommentConstant.TOP).equals(comment.getIsTop()));
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    /**
     * 发布评论
     * @param publishCommentDTO
     * @return
     */
    @Override
    public Long publish(PublishCommentDTO publishCommentDTO) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        PostRef postRef = postReadMapper.selectById(publishCommentDTO.getPostId());
        if (postRef == null || postRef.getDeleted() == null || postRef.getDeleted() != 0) {
            throw new BusinessException("帖子不存在");
        }
        String content = publishCommentDTO.getContent() == null ? "" : publishCommentDTO.getContent().trim();
        String imageUrl = publishCommentDTO.getImageUrl() == null ? "" : publishCommentDTO.getImageUrl().trim();
        if (!StringUtils.hasText(content) && !StringUtils.hasText(imageUrl)) {
            throw new BusinessException("评论内容不能为空");
        }
        if (content.codePointCount(0, content.length()) > CommentConstant.MAX_CONTENT_LENGTH) {
            throw new BusinessException("评论不能超过500字");
        }
        PublishCommentMessage message = new PublishCommentMessage();
        message.setId(IdUtil.getSnowflakeNextId());
        message.setPostId(postRef.getId());
        message.setBarId(postRef.getBarId());
        message.setUserId(userId);
        message.setImageUrl(imageUrl);
        message.setCreateTime(clock.millis());
        fillReply(message, publishCommentDTO.getReplyCommentId(), postRef.getId());
        fillContent(message, content);
        try {
            commentMqSender.send(message);
        } catch (Exception e) {
            throw new BusinessException("评论发送失败，请稍后重试");
        }
        return message.getId();
    }

    @Override
    public void delete(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException("无权删除该评论");
        }
        DeleteCommentMessage message = new DeleteCommentMessage();
        message.setMessageId(IdUtil.getSnowflakeNextId());
        message.setCommentId(commentId);
        message.setUserId(userId);
        message.setDeleteTime(clock.millis());
        try {
            commentMqSender.sendDelete(message);
        } catch (Exception e) {
            throw new BusinessException("删除失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Comment> handleDelete(DeleteCommentMessage message) {
        Long commentId = message.getCommentId();
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return List.of();
        }
        int rootDeleted = commentMapper.softDeleteById(commentId, message.getUserId());
        if (rootDeleted == 0) {
            log.warn("删除评论没删到行，userId={}，commentId={}", message.getUserId(), commentId);
            return List.of();
        }
        List<Comment> contentsToDelete = new ArrayList<>();
        contentsToDelete.add(comment);
        boolean isRoot = CommentLevelEnum.ONE.getCode().equals(comment.getLevel());
        long repliesDeleted = 0;
        if (isRoot) {
            repliesDeleted = cascadeDeleteReplies(comment, contentsToDelete);
        } else if (comment.getParentId() != null && comment.getParentId() > 0) {
            commentMapper.increaseReplyTotal(comment.getParentId(), -1);
        }
        commentLikeMapper.deleteByCommentIds(List.of(commentId));
        commentLikeCache.evict(commentId);
        long totalDeleted = rootDeleted + repliesDeleted;
        postCountMapper.addCommentCount(comment.getPostId(), (int) -totalDeleted);
        if (isRoot) {
            commentCountCache.incrCommentTotal(comment.getPostId(), -1);
            commentCountCache.deleteReplyTotal(commentId);
            commentCacheCoordinator.purgeCascadedRoot(comment);
        } else {
            commentCountCache.incrReplyTotal(comment.getParentId(), -1);
            commentCacheCoordinator.onCommentDeleted(comment);
        }
        log.info("删除评论落库完成，commentId={}，级联删除回复 {} 条", commentId, repliesDeleted);
        return contentsToDelete;
    }

    @Override
    public void deleteContents(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        for (Comment comment : comments) {
            if (!StringUtils.hasText(comment.getContentUuid()) || comment.getCreateTime() == null) {
                continue;
            }
            contentDeleteLimiter.acquire();
            try {
                keyValueRpcService.deleteCommentContent(comment.getPostId(),
                        comment.getCreateTime().format(YEAR_MONTH), comment.getContentUuid());
            } catch (RuntimeException e) {
                log.warn("删除评论正文异常，commentId={}", comment.getId(), e);
            }
        }
    }

    @Override
    public void like(Long userId, Long commentId) {
        ensureCommentExists(commentId);
        if (!commentLikeCache.like(userId, commentId)) {
            throw new BusinessException("已经点赞过该评论");
        }
        sendLike(userId, commentId, CommentLikeMqConstant.MQ_TAG);
    }

    @Override
    public void unlike(Long userId, Long commentId) {
        ensureCommentExists(commentId);
        if (!commentLikeCache.unlike(userId, commentId)) {
            throw new BusinessException("尚未点赞该评论");
        }
        sendLike(userId, commentId, CommentLikeMqConstant.MQ_UNLIKE_TAG);
    }

    /**
     * 批量保存评论
     * @param messages
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PublishCommentMessage> saveBatch(List<PublishCommentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        for (PublishCommentMessage message : messages) {
            normalizeContentId(message);
        }
        List<Long> insertedIds = commentMapper.insertIgnoreReturningIds(messages);
        if (insertedIds == null || insertedIds.isEmpty()) {
            return List.of();
        }
        Set<Long> freshIds = new HashSet<>(insertedIds);
        List<PublishCommentMessage> fresh = new ArrayList<>();
        List<PublishCommentMessage> withContent = new ArrayList<>();
        Map<Long, Integer> replyDeltas = new HashMap<>();
        Map<Long, Integer> postDeltas = new HashMap<>();
        for (PublishCommentMessage message : messages) {
            if (!freshIds.contains(message.getId())) {
                continue;
            }
            fresh.add(message);
            if (StringUtils.hasText(message.getContentUuid())) {
                withContent.add(message);
            }
            postDeltas.merge(message.getPostId(), 1, Integer::sum);
            if (CommentLevelEnum.TWO.getCode().equals(message.getLevel())
                    && message.getParentId() != null && message.getParentId() > 0) {
                replyDeltas.merge(message.getParentId(), 1, Integer::sum);
            }
        }
        if (!withContent.isEmpty()) {
            keyValueRpcService.batchSaveCommentContent(withContent);
        }
        replyDeltas.forEach(commentMapper::increaseReplyTotal);
        postDeltas.forEach(postCountMapper::addCommentCount);
        commentCacheCoordinator.onCommentsPersisted(fresh);
        for (PublishCommentMessage message : fresh) {
            Comment comment = commentMapper.selectById(message.getId());
            commentCacheCoordinator.appendToListCaches(comment);
        }
        return fresh;
    }

    @Override
    public void addHeat(List<PublishCommentMessage> freshComments) {
        if (freshComments == null || freshComments.isEmpty()) {
            return;
        }
        Map<Long, Integer> byBar = new HashMap<>();
        for (PublishCommentMessage message : freshComments) {
            if (message.getBarId() != null) {
                byBar.merge(message.getBarId(), 1, Integer::sum);
            }
        }
        byBar.forEach((barId, count) -> {
            try {
                stringRedisTemplate.opsForZSet().incrementScore(
                        CommentMqConstant.BAR_HOT_KEY, String.valueOf(barId), count);
            } catch (RuntimeException e) {
                log.warn("评论增加贴吧热度失败，barId={}，count={}", barId, count, e);
            }
        });
    }

    private void fillReply(PublishCommentMessage publishCommentMessage, Long replyCommentId, Long postId) {
        if (replyCommentId == null || replyCommentId == 0) {
            publishCommentMessage.setLevel(CommentLevelEnum.ONE.getCode());
            publishCommentMessage.setParentId(postId);
            publishCommentMessage.setReplyCommentId(CommentConstant.NONE);
            publishCommentMessage.setReplyUserId(CommentConstant.NONE);
            return;
        }
        Comment target = commentMapper.selectById(replyCommentId);
        if (target == null || target.getDeleted() == null || target.getDeleted() != 0
                || !postId.equals(target.getPostId())) {
            throw new BusinessException("要回复的评论不存在");
        }
        publishCommentMessage.setLevel(CommentLevelEnum.TWO.getCode());
        publishCommentMessage.setReplyCommentId(target.getId());
        publishCommentMessage.setReplyUserId(target.getUserId());
        if (CommentLevelEnum.ONE.getCode().equals(target.getLevel())) {
            publishCommentMessage.setParentId(target.getId());
        } else {
            publishCommentMessage.setParentId(target.getParentId());
        }
    }

    private void fillContent(PublishCommentMessage message, String content) {
        if (!StringUtils.hasText(content)) {
            message.setContent("");
            message.setContentUuid("");
            message.setContentEmpty(CommentConstant.CONTENT_EMPTY);
            message.setYearMonth(yearMonth(message.getCreateTime()));
            return;
        }
        message.setContent(content);
        message.setContentUuid(new UUID(0L, message.getId()).toString());
        message.setContentEmpty(CommentConstant.CONTENT_NOT_EMPTY);
        message.setYearMonth(yearMonth(message.getCreateTime()));
    }

    private String yearMonth(Long epochMillis) {
        return YEAR_MONTH.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private long cascadeDeleteReplies(Comment root, List<Comment> contentsToDelete) {
        long lastId = 0;
        long deleted = 0;
        while (true) {
            List<Comment> chunk = commentMapper.selectRepliesForCascade(root.getId(), lastId, CASCADE_CHUNK);
            if (chunk.isEmpty()) {
                break;
            }
            List<Long> ids = chunk.stream().map(Comment::getId).toList();
            List<Long> actuallyDeleted = commentMapper.softDeleteByIds(ids);
            if (!actuallyDeleted.isEmpty()) {
                deleted += actuallyDeleted.size();
                commentLikeMapper.deleteByCommentIds(actuallyDeleted);
                actuallyDeleted.forEach(commentLikeCache::evict);
                commentCacheCoordinator.evictDetails(actuallyDeleted);
            }
            contentsToDelete.addAll(chunk);
            lastId = ids.get(ids.size() - 1);
        }
        return deleted;
    }

    private void ensureCommentExists(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || Integer.valueOf(1).equals(comment.getDeleted())) {
            throw new BusinessException("评论不存在");
        }
    }

    private void sendLike(Long userId, Long commentId, String action) {
        CommentLikeMessage message = new CommentLikeMessage();
        message.setId(IdUtil.getSnowflakeNextId());
        message.setUserId(userId);
        message.setCommentId(commentId);
        message.setLikeTime(clock.millis());
        message.setAction(action);
        String destination = CommentLikeMqConstant.MQ_UNLIKE_TAG.equals(action)
                ? CommentLikeMqConstant.MQ_UNLIKE_DESTINATION
                : CommentLikeMqConstant.MQ_DESTINATION;
        SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                destination, message, String.valueOf(userId));
        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new BusinessException("点赞请求失败，请稍后重试");
        }
    }
    // 把contentId转化为UUID
    private static void normalizeContentId(PublishCommentMessage message) {
        if (!StringUtils.hasText(message.getContent()) || message.getId() == null) {
            return;
        }
        if (isUuid(message.getContentUuid())) {
            return;
        }
        message.setContentUuid(new UUID(0L, message.getId()).toString());
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
