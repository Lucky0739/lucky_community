package com.lucky.interaction.service.impl;

import com.lucky.common.exception.BusinessException;
import com.lucky.common.vo.PageResult;
import com.lucky.interaction.cache.FavoriteCache;
import com.lucky.interaction.cache.ThumbCache;
import com.lucky.interaction.dto.UserPostListQueryDTO;
import com.lucky.interaction.entity.PostSummaryRow;
import com.lucky.interaction.entity.UserPostInteraction;
import com.lucky.interaction.mapper.FavoriteMapper;
import com.lucky.interaction.mapper.PostSummaryReadMapper;
import com.lucky.interaction.mapper.ThumbMapper;
import com.lucky.interaction.mapper.UserReadMapper;
import com.lucky.interaction.service.UserInteractionPostQueryService;
import com.lucky.interaction.support.entity.UserBrief;
import com.lucky.interaction.vo.UserPostSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserInteractionPostQueryServiceImpl implements UserInteractionPostQueryService {

    private final UserReadMapper userReadMapper;
    private final ThumbMapper thumbMapper;
    private final FavoriteMapper favoriteMapper;
    private final PostSummaryReadMapper postSummaryReadMapper;
    private final ThumbCache thumbCache;
    private final FavoriteCache favoriteCache;

    @Override
    public PageResult<UserPostSummaryVO> pageLikedPosts(Long viewerId, UserPostListQueryDTO query) {
        Long ownerId = query.getUserId() == null ? viewerId : query.getUserId();
        return pagePosts(ownerId, viewerId, query, InteractionKind.LIKED);
    }

    @Override
    public PageResult<UserPostSummaryVO> pageFavoritedPosts(Long viewerId, UserPostListQueryDTO query) {
        Long ownerId = query.getUserId() == null ? viewerId : query.getUserId();
        return pagePosts(ownerId, viewerId, query, InteractionKind.FAVORITED);
    }

    private PageResult<UserPostSummaryVO> pagePosts(Long ownerId, Long viewerId,
                                                    UserPostListQueryDTO query,
                                                    InteractionKind kind) {
        if (!userReadMapper.existsById(ownerId)) {
            throw new BusinessException("用户不存在");
        }
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 50);
        int offset = (pageNum - 1) * pageSize;

        long total = kind == InteractionKind.LIKED
                ? thumbMapper.countVisibleByUserId(ownerId)
                : favoriteMapper.countVisibleByUserId(ownerId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }

        List<UserPostInteraction> interactions = kind == InteractionKind.LIKED
                ? thumbMapper.selectVisiblePageByUserId(ownerId, offset, pageSize)
                : favoriteMapper.selectVisiblePageByUserId(ownerId, offset, pageSize);
        if (interactions.isEmpty()) {
            return new PageResult<>(List.of(), total, pageNum, pageSize);
        }

        List<Long> postIds = interactions.stream().map(UserPostInteraction::getPostId).toList();
        Map<Long, PostSummaryRow> posts = postSummaryReadMapper.selectApprovedByIds(postIds).stream()
                .collect(Collectors.toMap(PostSummaryRow::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> barNames = toBarNameMap(postSummaryReadMapper.selectBarNamesByIds(
                posts.values().stream().map(PostSummaryRow::getBarId).distinct().toList()));

        List<Long> authorIds = posts.values().stream().map(PostSummaryRow::getUserId).distinct().toList();
        Map<Long, UserBrief> authors = new HashMap<>();
        List<UserBrief> briefs = userReadMapper.selectByIds(authorIds);
        if (briefs != null) {
            for (UserBrief brief : briefs) {
                authors.put(brief.getId(), brief);
            }
        }

        Map<Long, CountRow> counts = loadCounts(postIds);
        Set<Long> liked = resolveLiked(viewerId, postIds);
        Set<Long> favorited = resolveFavorited(viewerId, postIds);

        List<UserPostSummaryVO> records = new ArrayList<>(interactions.size());
        for (UserPostInteraction interaction : interactions) {
            PostSummaryRow post = posts.get(interaction.getPostId());
            if (post == null) {
                continue;
            }
            UserBrief author = authors.get(post.getUserId());
            UserPostSummaryVO vo = new UserPostSummaryVO();
            vo.setPostId(post.getId());
            vo.setBarId(post.getBarId());
            vo.setBarName(barNames.get(post.getBarId()));
            vo.setUserId(post.getUserId());
            if (author != null) {
                vo.setNickName(author.getNickName());
                vo.setIcon(author.getIcon());
            }
            vo.setTitle(post.getTitle());
            vo.setContentPreview(post.getContentPreview());
            vo.setCoverImage(post.getCoverImage());
            vo.setImageCount(post.getImageCount());
            vo.setCreateTime(post.getCreateTime());
            vo.setInteractionTime(interaction.getInteractionTime());

            CountRow countRow = counts.getOrDefault(post.getId(), CountRow.empty());
            vo.setLikeCount(countRow.likeCount());
            vo.setFavoriteCount(countRow.favoriteCount());
            vo.setCommentCount(countRow.commentCount());
            vo.setLiked(liked.contains(post.getId()));
            vo.setFavorited(favorited.contains(post.getId()));
            if (kind == InteractionKind.LIKED) {
                vo.setLiked(true);
            } else {
                vo.setFavorited(true);
            }
            records.add(vo);
        }
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    private Map<Long, CountRow> loadCounts(List<Long> postIds) {
        Map<Long, CountRow> result = new HashMap<>();
        for (Long postId : postIds) {
            result.put(postId, CountRow.empty());
        }
        for (Map<String, Object> row : postSummaryReadMapper.selectCountsByPostIds(postIds)) {
            Long postId = toLong(row.get("postId"));
            CountRow countRow = result.getOrDefault(postId, CountRow.empty());
            countRow = new CountRow(
                    toLong(row.get("likeCount")),
                    toLong(row.get("favoriteCount")),
                    toLong(row.get("commentCount")));
            result.put(postId, countRow);
        }
        for (Map<String, Object> row : postSummaryReadMapper.countCommentsByPostIds(postIds)) {
            Long postId = toLong(row.get("postId"));
            CountRow countRow = result.getOrDefault(postId, CountRow.empty());
            if (countRow.commentCount() == 0L) {
                result.put(postId, new CountRow(countRow.likeCount(), countRow.favoriteCount(), toLong(row.get("cnt"))));
            }
        }
        for (Long postId : postIds) {
            CountRow countRow = result.get(postId);
            Long redisLike = thumbCache.readLikeCount(postId);
            Long redisFavorite = favoriteCache.readFavoriteCount(postId);
            long likeCount = redisLike != null ? redisLike : countRow.likeCount();
            long favoriteCount = redisFavorite != null ? redisFavorite : countRow.favoriteCount();
            result.put(postId, new CountRow(likeCount, favoriteCount, countRow.commentCount()));
        }
        return result;
    }

    private Set<Long> resolveLiked(Long viewerId, List<Long> postIds) {
        if (viewerId == null || postIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = thumbMapper.listLikedPostIds(viewerId, postIds);
        return ids == null ? Set.of() : new HashSet<>(ids);
    }

    private Set<Long> resolveFavorited(Long viewerId, List<Long> postIds) {
        if (viewerId == null || postIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = favoriteMapper.listFavoritedPostIds(viewerId, postIds);
        return ids == null ? Set.of() : new HashSet<>(ids);
    }

    private static Map<Long, String> toBarNameMap(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(toLong(row.get("barId")), String.valueOf(row.get("barName")));
        }
        return result;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private enum InteractionKind {
        LIKED, FAVORITED
    }

    private record CountRow(long likeCount, long favoriteCount, long commentCount) {
        static CountRow empty() {
            return new CountRow(0L, 0L, 0L);
        }
    }
}
