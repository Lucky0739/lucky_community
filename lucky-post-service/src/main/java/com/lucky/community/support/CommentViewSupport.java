package com.lucky.community.support;

import com.lucky.common.constant.CommentConstant;
import com.lucky.community.comment.vo.CommentVO;
import com.lucky.community.support.entity.UserBrief;
import com.lucky.community.support.mapper.CommentViewMapper;
import com.lucky.community.support.mapper.UserReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentViewSupport {

    private final CommentViewMapper commentViewMapper;
    private final UserReadMapper userReadMapper;

    public record RootCommentPage(List<CommentVO> records, long total) {
    }

    public RootCommentPage loadRootFirstPage(Long postId, int pageSize) {
        long total = commentViewMapper.countRootComments(postId);
        List<Map<String, Object>> rows = commentViewMapper.selectRootComments(postId, pageSize);
        if (rows.isEmpty()) {
            return new RootCommentPage(List.of(), total);
        }
        List<String> uuids = rows.stream()
                .map(r -> (String) r.get("contentUuid"))
                .filter(u -> u != null && !u.isBlank())
                .toList();
        Map<String, String> contents = Map.of();
        if (!uuids.isEmpty()) {
            contents = commentViewMapper.selectContentsByUuids(uuids).stream()
                    .collect(Collectors.toMap(
                            m -> (String) m.get("contentUuid"),
                            m -> (String) m.get("content"),
                            (a, b) -> a));
        }
        Set<Long> userIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            userIds.add(toLong(row.get("userId")));
            Long replyUserId = toLong(row.get("replyUserId"));
            if (replyUserId != null && replyUserId != CommentConstant.NONE) {
                userIds.add(replyUserId);
            }
        }
        Map<Long, UserBrief> users = userReadMapper.selectByIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(UserBrief::getId, u -> u));
        List<CommentVO> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            CommentVO vo = new CommentVO();
            vo.setCommentId(toLong(row.get("commentId")));
            vo.setPostId(toLong(row.get("postId")));
            vo.setUserId(toLong(row.get("userId")));
            UserBrief author = users.get(vo.getUserId());
            vo.setNickName(author == null ? null : author.getNickName());
            vo.setIcon(author == null ? null : author.getIcon());
            vo.setLevel(toInt(row.get("level")));
            vo.setParentId(toLong(row.get("parentId")));
            vo.setRootId(CommentConstant.NONE);
            vo.setReplyCommentId(toLong(row.get("replyCommentId")));
            vo.setReplyUserId(toLong(row.get("replyUserId")));
            vo.setReplyCount(toLong(row.get("replyTotal")));
            vo.setLikeCount(toLong(row.get("likeTotal")));
            vo.setTop(Integer.valueOf(CommentConstant.TOP).equals(toInt(row.get("isTop"))));
            vo.setCreateTime((LocalDateTime) row.get("createTime"));
            String uuid = (String) row.get("contentUuid");
            vo.setContent(uuid == null || uuid.isBlank() ? "" : contents.getOrDefault(uuid, ""));
            String imageUrl = (String) row.get("imageUrl");
            vo.setImageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageUrl);
            vo.setLiked(false);
            records.add(vo);
        }
        return new RootCommentPage(records, total);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
