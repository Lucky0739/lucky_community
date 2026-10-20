package com.lucky.interaction.service.impl;

import com.lucky.common.vo.PageResult;
import com.lucky.interaction.cache.FavoriteCache;
import com.lucky.interaction.cache.FollowCache;
import com.lucky.interaction.cache.ThumbCache;
import com.lucky.interaction.entity.PostSummaryRow;
import com.lucky.interaction.mapper.BarReadMapper;
import com.lucky.interaction.mapper.FavoriteMapper;
import com.lucky.interaction.mapper.FollowMapper;
import com.lucky.interaction.mapper.PostSummaryReadMapper;
import com.lucky.interaction.mapper.ThumbMapper;
import com.lucky.interaction.mapper.UserReadMapper;
import com.lucky.interaction.service.FollowFeedCardQueryService;
import com.lucky.interaction.service.FollowFeedService;
import com.lucky.interaction.support.entity.BarBrief;
import com.lucky.interaction.support.entity.UserBrief;
import com.lucky.interaction.vo.FollowFeedCardVO;
import com.lucky.interaction.vo.FollowFeedItemVO;
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
public class FollowFeedCardQueryServiceImpl implements FollowFeedCardQueryService {

    private final FollowFeedService followFeedService;
    private final PostSummaryReadMapper postSummaryReadMapper;
    private final BarReadMapper barReadMapper;
    private final UserReadMapper userReadMapper;
    private final ThumbMapper thumbMapper;
    private final FavoriteMapper favoriteMapper;
    private final ThumbCache thumbCache;
    private final FavoriteCache favoriteCache;
    private final FollowCache followCache;
    private final FollowMapper followMapper;

    @Override
    public PageResult<FollowFeedCardVO> pageCards(Long viewerId, int pageNo, int pageSize) {
        PageResult<FollowFeedItemVO> feed = followFeedService.page(viewerId, pageNo, pageSize);
        List<FollowFeedItemVO> items = feed.getRecords();
        if (items == null || items.isEmpty()) {
            return new PageResult<>(List.of(), feed.getTotal(), feed.getPageNum(), feed.getPageSize());
        }

        List<Long> postIds = items.stream().map(FollowFeedItemVO::getPostId).toList();
        Map<Long, PostSummaryRow> posts = postSummaryReadMapper.selectApprovedByIds(postIds).stream()
                .collect(Collectors.toMap(PostSummaryRow::getId, Function.identity(), (a, b) -> a));

        List<Long> barIds = items.stream().map(FollowFeedItemVO::getBarId).distinct().toList();
        Map<Long, BarBrief> bars = new HashMap<>();
        List<BarBrief> barBriefs = barReadMapper.selectByIds(barIds);
        if (barBriefs != null) {
            for (BarBrief brief : barBriefs) {
                bars.put(brief.getId(), brief);
            }
        }

        List<Long> authorIds = posts.values().stream().map(PostSummaryRow::getUserId).distinct().toList();
        Map<Long, UserBrief> authors = new HashMap<>();
        if (!authorIds.isEmpty()) {
            List<UserBrief> userBriefs = userReadMapper.selectByIds(authorIds);
            if (userBriefs != null) {
                for (UserBrief brief : userBriefs) {
                    authors.put(brief.getId(), brief);
                }
            }
        }

        Map<Long, CountRow> counts = loadCounts(postIds);
        Set<Long> liked = resolveLiked(viewerId, postIds);
        Set<Long> favorited = resolveFavorited(viewerId, postIds);
        Set<Long> followedBars = resolveFollowedBars(viewerId, barIds);

        List<FollowFeedCardVO> records = new ArrayList<>(items.size());
        for (FollowFeedItemVO item : items) {
            PostSummaryRow post = posts.get(item.getPostId());
            if (post == null) {
                continue;
            }
            BarBrief bar = bars.get(item.getBarId());
            UserBrief author = authors.get(post.getUserId());
            CountRow countRow = counts.getOrDefault(post.getId(), CountRow.empty());

            FollowFeedCardVO card = new FollowFeedCardVO();
            card.setBarId(item.getBarId());
            if (bar != null) {
                card.setBarName(bar.getName());
                card.setBarAvatar(bar.getAvatar());
                card.setBarFollowCount(bar.getFollowCount());
                card.setBarPostCount(bar.getPostCount());
            }
            card.setFollowed(followedBars.contains(item.getBarId()));

            card.setPostId(post.getId());
            card.setUserId(post.getUserId());
            if (author != null) {
                card.setNickName(author.getNickName());
                card.setIcon(author.getIcon());
            }
            card.setTitle(post.getTitle());
            card.setContentPreview(post.getContentPreview());
            card.setCoverImage(post.getCoverImage());
            card.setImageCount(post.getImageCount());
            card.setCreateTime(post.getCreateTime());
            card.setLikeCount(countRow.likeCount());
            card.setCommentCount(countRow.commentCount());
            card.setFavoriteCount(countRow.favoriteCount());
            card.setLiked(liked.contains(post.getId()));
            card.setFavorited(favorited.contains(post.getId()));
            records.add(card);
        }
        return new PageResult<>(records, feed.getTotal(), feed.getPageNum(), feed.getPageSize());
    }

    private Map<Long, CountRow> loadCounts(List<Long> postIds) {
        Map<Long, CountRow> result = new HashMap<>();
        for (Long postId : postIds) {
            result.put(postId, CountRow.empty());
        }
        for (Map<String, Object> row : postSummaryReadMapper.selectCountsByPostIds(postIds)) {
            Long postId = toLong(row.get("postId"));
            CountRow countRow = result.getOrDefault(postId, CountRow.empty());
            result.put(postId, new CountRow(
                    toLong(row.get("likeCount")),
                    toLong(row.get("favoriteCount")),
                    toLong(row.get("commentCount"))));
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

    private Set<Long> resolveFollowedBars(Long viewerId, List<Long> barIds) {
        Set<Long> followed = new HashSet<>();
        if (viewerId == null || barIds.isEmpty()) {
            return followed;
        }
        try {
            if (followCache.hasFollowZSet(viewerId)) {
                for (Long barId : barIds) {
                    if (followCache.isFollowed(viewerId, barId)) {
                        followed.add(barId);
                    }
                }
                return followed;
            }
        } catch (RuntimeException ignored) {
            // 回源数据库
        }
        List<Long> ids = followMapper.selectFollowedBarIds(viewerId, barIds);
        if (ids != null) {
            followed.addAll(ids);
        }
        return followed;
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

    private record CountRow(long likeCount, long favoriteCount, long commentCount) {
        static CountRow empty() {
            return new CountRow(0L, 0L, 0L);
        }
    }
}
