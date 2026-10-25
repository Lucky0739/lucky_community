package com.lucky.comment.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.common.mq.RocketMqNameServer;
import com.lucky.comment.cache.CommentLikeCache;
import com.lucky.comment.constant.CommentLikeMqConstant;
import com.lucky.comment.entity.CommentLike;
import com.lucky.comment.mapper.CommentLikeMapper;
import com.lucky.comment.mapper.CommentMapper;
import com.lucky.comment.mq.message.CommentLikeMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentLikeBatchConsumer {

    private final CommentLikeMapper commentLikeMapper;
    private final CommentMapper commentMapper;
    private final CommentLikeCache commentLikeCache;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private DefaultLitePullConsumer consumer;
    private Thread worker;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultLitePullConsumer(CommentLikeMqConstant.MQ_CONSUMER_GROUP);
        RocketMqNameServer.apply(consumer, namesrvAddr);
        consumer.setAutoCommit(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setPullBatchSize(CommentLikeMqConstant.CONSUME_BATCH_MAX);
        consumer.subscribe(CommentLikeMqConstant.MQ_TOPIC,
                CommentLikeMqConstant.MQ_TAG + " || " + CommentLikeMqConstant.MQ_UNLIKE_TAG);
        consumer.start();
        worker = new Thread(this::loop, "comment-like-pull");
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            List<MessageExt> batch;
            try {
                batch = consumer.poll(3000);
            } catch (Exception e) {
                log.error("拉取评论点赞消息失败", e);
                continue;
            }
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            try {
                persist(parse(batch));
                consumer.commitSync();
            } catch (Exception e) {
                log.error("评论点赞批量落库失败，size={}", batch.size(), e);
            }
        }
    }

    private List<CommentLikeMessage> parse(List<MessageExt> msgs) {
        List<CommentLikeMessage> messages = new ArrayList<>(msgs.size());
        for (MessageExt msg : msgs) {
            try {
                messages.add(objectMapper.readValue(
                        new String(msg.getBody(), StandardCharsets.UTF_8), CommentLikeMessage.class));
            } catch (Exception e) {
                log.error("评论点赞消息解析失败，msgId={}", msg.getMsgId(), e);
            }
        }
        return messages;
    }

    private void persist(List<CommentLikeMessage> messages) {
        List<CommentLikeMessage> netOps = merge(messages);
        if (netOps.isEmpty()) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> applyBatch(netOps));
    }

    private List<CommentLikeMessage> merge(List<CommentLikeMessage> messages) {
        Map<String, List<CommentLikeMessage>> groups = new LinkedHashMap<>();
        for (CommentLikeMessage message : messages) {
            if (message.getUserId() == null || message.getCommentId() == null) {
                continue;
            }
            groups.computeIfAbsent(message.getUserId() + ":" + message.getCommentId(),
                    k -> new ArrayList<>()).add(message);
        }
        List<CommentLikeMessage> netOps = new ArrayList<>();
        for (List<CommentLikeMessage> group : groups.values()) {
            if (group.size() % 2 == 1) {
                netOps.add(group.get(group.size() - 1));
            }
        }
        return netOps;
    }

    private void applyBatch(List<CommentLikeMessage> netOps) {
        List<CommentLike> toInsert = new ArrayList<>();
        List<CommentLike> toDelete = new ArrayList<>();
        for (CommentLikeMessage message : netOps) {
            CommentLike like = toEntity(message);
            if (CommentLikeMqConstant.MQ_UNLIKE_TAG.equals(message.getAction())) {
                toDelete.add(like);
            } else {
                toInsert.add(like);
            }
        }
        Map<Long, Integer> deltas = new HashMap<>();
        if (!toInsert.isEmpty()) {
            for (CommentLike inserted : commentLikeMapper.batchInsertIgnore(toInsert)) {
                deltas.merge(inserted.getCommentId(), 1, Integer::sum);
            }
        }
        if (!toDelete.isEmpty()) {
            for (CommentLike deleted : commentLikeMapper.batchDeleteByUserAndComment(toDelete)) {
                deltas.merge(deleted.getCommentId(), -1, Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            if (entry.getValue() == 0) {
                continue;
            }
            Long commentId = entry.getKey();
            Long redisCount = commentLikeCache.readCount(commentId);
            if (redisCount != null) {
                commentMapper.updateLikeTotal(commentId, Math.max(redisCount, 0L));
            }
        }
    }

    private CommentLike toEntity(CommentLikeMessage message) {
        CommentLike like = new CommentLike();
        like.setId(message.getId());
        like.setUserId(message.getUserId());
        like.setCommentId(message.getCommentId());
        like.setCreateTime(toTime(message.getLikeTime()));
        return like;
    }

    private LocalDateTime toTime(Long millis) {
        if (millis == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(worker)) {
            worker.interrupt();
        }
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("关闭评论点赞消费者失败", e);
            }
        }
    }
}
