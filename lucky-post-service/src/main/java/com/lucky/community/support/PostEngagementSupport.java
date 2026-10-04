package com.lucky.community.support;

import com.lucky.community.support.mapper.PostEngagementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostEngagementSupport {

    private final PostEngagementMapper postEngagementMapper;

    public Map<Long, Long> getLikeCounts(Collection<Long> postIds) {
        return toCountMap(postIds, postEngagementMapper.countLikesByPostIds(List.copyOf(postIds)));
    }

    public Set<Long> listLikedPostIds(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(postEngagementMapper.listLikedPostIds(userId, List.copyOf(postIds)));
    }

    public Map<Long, Long> getFavoriteCounts(Collection<Long> postIds) {
        return toCountMap(postIds, postEngagementMapper.countFavoritesByPostIds(List.copyOf(postIds)));
    }

    public Set<Long> listFavoritedPostIds(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(postEngagementMapper.listFavoritedPostIds(userId, List.copyOf(postIds)));
    }

    public Map<Long, Long> getCommentCounts(Collection<Long> postIds) {
        return toCountMap(postIds, postEngagementMapper.countCommentsByPostIds(List.copyOf(postIds)));
    }

    private Map<Long, Long> toCountMap(Collection<Long> postIds, List<Map<String, Object>> rows) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(toLong(row.get("postId")), toLong(row.get("cnt")));
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
}
