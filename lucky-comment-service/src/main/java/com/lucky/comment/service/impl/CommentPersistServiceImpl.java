package com.lucky.comment.service.impl;

import com.lucky.comment.enums.CommentLevelEnum;
import com.lucky.comment.mapper.CommentMapper;
import com.lucky.comment.mapper.PostCountMapper;
import com.lucky.comment.mq.PublishCommentMessage;
import com.lucky.comment.rpc.KeyValueRpcService;
import com.lucky.comment.service.CommentPersistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentPersistServiceImpl implements CommentPersistService {

    private final CommentMapper commentMapper;
    private final PostCountMapper postCountMapper;
    private final KeyValueRpcService keyValueRpcService;

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
        return fresh;
    }

    /**
     * Cassandra 的 content_id 必须是 UUID。消息里如果是「0」这类标记，改成高位为 0、低位为评论雪花 ID。
     */
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
