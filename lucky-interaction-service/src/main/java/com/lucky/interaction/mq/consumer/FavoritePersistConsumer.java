package com.lucky.interaction.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.common.mq.RocketMqNameServer;
import com.lucky.interaction.cache.FavoriteCache;
import com.lucky.interaction.constant.FavoriteConstant;
import com.lucky.interaction.entity.Favorite;
import com.lucky.interaction.mapper.FavoriteMapper;
import com.lucky.interaction.mq.message.FavoriteMessage;
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
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class FavoritePersistConsumer {

    private final FavoriteMapper favoriteMapper;
    private final FavoriteCache favoriteCache;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private DefaultLitePullConsumer consumer;
    private Thread worker;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultLitePullConsumer(FavoriteConstant.MQ_CONSUMER_GROUP);
        RocketMqNameServer.apply(consumer, namesrvAddr);
        consumer.setAutoCommit(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setPullBatchSize(FavoriteConstant.PERSIST_BATCH_SIZE);
        consumer.subscribe(FavoriteConstant.MQ_TOPIC,
                FavoriteConstant.MQ_TAG + " || " + FavoriteConstant.MQ_UNFAVORITE_TAG);
        consumer.start();

        worker = new Thread(this::loop, "favorite-pull");
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            List<MessageExt> batch;
            try {
                batch = collectBatch();
            } catch (Exception e) {
                log.error("拉取收藏消息失败，稍后重试", e);
                continue;
            }
            if (batch.isEmpty()) {
                continue;
            }
            try {
                persistWithDeadlockRetry(parse(batch));
                consumer.commitSync();
            } catch (Exception e) {
                log.error("收藏批量落库失败，不提交，稍后重拉，size={}", batch.size(), e);
            }
        }
    }

    private List<MessageExt> collectBatch() {
        long deadline = System.currentTimeMillis() + FavoriteConstant.PERSIST_LINGER_SECONDS * 1000L;
        List<MessageExt> batch = new ArrayList<>();
        while (batch.size() < FavoriteConstant.PERSIST_BATCH_SIZE) {
            long wait = deadline - System.currentTimeMillis();
            if (wait <= 0) {
                break;
            }
            batch.addAll(consumer.poll(wait));
        }
        return batch;
    }

    private List<FavoriteMessage> parse(List<MessageExt> msgs) {
        List<FavoriteMessage> messages = new ArrayList<>(msgs.size());
        for (MessageExt msg : msgs) {
            try {
                messages.add(objectMapper.readValue(
                        new String(msg.getBody(), StandardCharsets.UTF_8), FavoriteMessage.class));
            } catch (Exception e) {
                log.error("收藏消息无法解析，已跳过，msgId={}", msg.getMsgId(), e);
            }
        }
        return messages;
    }

    /**
     * 落库失败若为数据库死锁，在同批消息上有限次重试；仍失败则抛出，由外层不 commit、MQ 重拉。
     */
    private void persistWithDeadlockRetry(List<FavoriteMessage> messages) throws Exception {
        int maxAttempts = FavoriteConstant.PERSIST_DEADLOCK_RETRY_MAX;
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                persist(messages);
                return;
            } catch (Exception e) {
                last = e;
                if (attempt >= maxAttempts || !isDeadlock(e)) {
                    throw e;
                }
                log.warn("收藏批量落库死锁，第 {}/{} 次重试，本批净消息 {} 条",
                        attempt, maxAttempts, messages.size(), e);
                pauseBeforeDeadlockRetry(attempt);
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private static void pauseBeforeDeadlockRetry(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("收藏落库死锁重试被中断", e);
        }
    }

    private static boolean isDeadlock(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if ("40P01".equals(state) || "40001".equals(state) || sqlException.getErrorCode() == 1213) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("deadlock")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void persist(List<FavoriteMessage> messages) {
        List<FavoriteMessage> netOps = merge(messages);
        if (netOps.isEmpty()) {
            log.info("{} 条收藏消息全部互相抵消，不写库", messages.size());
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> applyBatch(netOps));
    }

    private List<FavoriteMessage> merge(List<FavoriteMessage> messages) {
        Map<String, List<FavoriteMessage>> groups = new LinkedHashMap<>();
        for (FavoriteMessage message : messages) {
            if (message.getUserId() == null || message.getPostId() == null) {
                continue;
            }
            groups.computeIfAbsent(message.getUserId() + ":" + message.getPostId(),
                    k -> new ArrayList<>()).add(message);
        }
        List<FavoriteMessage> netOps = new ArrayList<>();
        for (List<FavoriteMessage> group : groups.values()) {
            if (group.size() % 2 == 1) {
                netOps.add(group.get(group.size() - 1));
            }
        }
        return netOps;
    }

    private void applyBatch(List<FavoriteMessage> netOps) {
        List<Favorite> toInsert = new ArrayList<>();
        List<Favorite> toDelete = new ArrayList<>();
        for (FavoriteMessage message : netOps) {
            String action = message.getAction();
            if (FavoriteConstant.MQ_UNFAVORITE_TAG.equals(action)) {
                toDelete.add(toFavorite(message));
            } else if (FavoriteConstant.MQ_TAG.equals(action)) {
                toInsert.add(toFavorite(message));
            } else {
                log.error("未知的收藏消息 action，已丢弃，action={}，userId={}，postId={}",
                        action, message.getUserId(), message.getPostId());
            }
        }
        if (!toInsert.isEmpty()) {
            favoriteMapper.batchInsertIgnore(toInsert);
        }
        if (!toDelete.isEmpty()) {
            favoriteMapper.batchDeleteByUserAndPost(toDelete);
        }
        for (FavoriteMessage message : netOps) {
            favoriteCache.clearUnsynced(message.getUserId(), message.getPostId());
        }
        log.info("收藏批量落库完成，净操作 {} 条", netOps.size());
    }

    private Favorite toFavorite(FavoriteMessage message) {
        Favorite favorite = new Favorite();
        favorite.setId(message.getId());
        favorite.setUserId(message.getUserId());
        favorite.setPostId(message.getPostId());
        favorite.setCreateTime(toCreateTime(message.getFavoriteTime()));
        return favorite;
    }

    private LocalDateTime toCreateTime(Long favoriteTime) {
        if (favoriteTime == null) {
            return LocalDateTime.now(clock);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(favoriteTime), clock.getZone());
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
                log.error("关闭收藏消费者失败", e);
            }
        }
    }
}
