package com.lucky.interaction.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucky.common.mq.RocketMqNameServer;
import com.lucky.interaction.cache.ThumbCache;
import com.lucky.interaction.constant.ThumbConstant;
import com.lucky.interaction.entity.Thumb;
import com.lucky.interaction.mapper.ThumbMapper;
import com.lucky.interaction.mq.message.ThumbMessage;
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

/**
 * 点赞落库。用 {@link DefaultLitePullConsumer} 主动拉取：攒够 {@code PERSIST_BATCH_SIZE} 条
 * <p>帖子点赞数由 {@link com.lucky.interaction.service.LikeCountFlushService} 按 500 次变更或 10 秒延迟刷 tb_post_count。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbPersistConsumer {

    /** 点赞记录（tb_thumb）读写 */
    private final ThumbMapper thumbMapper;

    /** 清待落库标记 */
    private final ThumbCache thumbCache;

    /** 解析 MQ 消息体 */
    private final ObjectMapper objectMapper;

    /** thumbTime 时间戳转 LocalDateTime 用 */
    private final Clock clock;

    /** 整批落库包一个事务 */
    private final PlatformTransactionManager transactionManager;

    /** RocketMQ NameServer 地址 */
    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    /** 主动拉取的消费者（轻量拉模式） */
    private DefaultLitePullConsumer consumer;

    /** 拉取 + 落库的工作线程 */
    private Thread worker;

    /**
     * 启动消费者并拉起工作线程。关掉自动提交，由 {@link #loop()} 处理成功后再手动 commitSync。
     */
    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultLitePullConsumer(ThumbConstant.MQ_CONSUMER_GROUP);
        RocketMqNameServer.apply(consumer, namesrvAddr);
        consumer.setAutoCommit(false);                                  // 处理成功才手动提交
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setPullBatchSize(ThumbConstant.PERSIST_BATCH_SIZE);    // 一次最多拉这么多
        consumer.subscribe(ThumbConstant.MQ_TOPIC,
                ThumbConstant.MQ_TAG + " || " + ThumbConstant.MQ_UNLIKE_TAG);
        consumer.start();

        worker = new Thread(this::loop, "thumb-pull");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 拉取-落库主循环：攒批 → 抵消 + 落库 → 提交。
     * 落库失败**不提交**，这批消息下轮会被重新拉到（不会像纯内存攒批那样丢）。
     */
    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            List<MessageExt> batch;
            try {
                batch = collectBatch();
            } catch (Exception e) {
                log.error("拉取点赞消息失败，稍后重试", e);
                continue;
            }
            if (batch.isEmpty()) {
                continue;
            }
            try {
                persistWithDeadlockRetry(parse(batch));
                consumer.commitSync();          // 成功才提交；失败落到下面的 catch，不提交
            } catch (Exception e) {
                log.error("点赞批量落库失败，不提交，稍后重拉，size={}", batch.size(), e);
            }
        }
    }

    /**
     * 攒批：循环 poll 直到攒够 {@code PERSIST_BATCH_SIZE} 条 或 满 {@code PERSIST_LINGER_SECONDS} 秒。
     */
    private List<MessageExt> collectBatch() {
        long deadline = System.currentTimeMillis() + ThumbConstant.PERSIST_LINGER_SECONDS * 1000L;
        List<MessageExt> batch = new ArrayList<>();
        while (batch.size() < ThumbConstant.PERSIST_BATCH_SIZE) {
            long wait = deadline - System.currentTimeMillis();
            if (wait <= 0) {
                break;
            }
            batch.addAll(consumer.poll(wait));
        }
        return batch;
    }

    /**
     * 反序列化这一批消息体。单条解析失败只跳过那一条，不影响整批。
     */
    private List<ThumbMessage> parse(List<MessageExt> msgs) {
        List<ThumbMessage> messages = new ArrayList<>(msgs.size());
        for (MessageExt msg : msgs) {
            try {
                messages.add(objectMapper.readValue(
                        new String(msg.getBody(), StandardCharsets.UTF_8), ThumbMessage.class));
            } catch (Exception e) {
                log.error("点赞消息无法解析，已跳过，msgId={}", msg.getMsgId(), e);
            }
        }
        return messages;
    }

    /**
     * 落库失败若为数据库死锁，在同批消息上有限次重试；仍失败则抛出，由外层不 commit、MQ 重拉。
     */
    private void persistWithDeadlockRetry(List<ThumbMessage> messages) throws Exception {
        int maxAttempts = ThumbConstant.PERSIST_DEADLOCK_RETRY_MAX;
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
                log.warn("点赞批量落库死锁，第 {}/{} 次重试，本批净消息 {} 条",
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
            throw new IllegalStateException("点赞落库死锁重试被中断", e);
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

    /**
     * 批内先按 (userId, postId) 抵消，再在一个事务里批量落库。
     * 全部抵消时直接返回，不碰数据库。
     */
    private void persist(List<ThumbMessage> messages) {
        List<ThumbMessage> netOps = merge(messages);
        if (netOps.isEmpty()) {
            log.info("{} 条点赞消息全部互相抵消，不写库", messages.size());
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> applyBatch(netOps));
    }
    /** 归并：按 (userId, postId) 分组，偶数次抵消，奇数次保留最后一次动作 */
    private List<ThumbMessage> merge(List<ThumbMessage> messages) {
        Map<String, List<ThumbMessage>> groups = new LinkedHashMap<>();
        for (ThumbMessage message : messages) {
            if (message.getUserId() == null || message.getPostId() == null) {
                continue;
            }
            groups.computeIfAbsent(message.getUserId() + ":" + message.getPostId(),
                    k -> new ArrayList<>()).add(message);
        }
        List<ThumbMessage> netOps = new ArrayList<>();
        for (List<ThumbMessage> group : groups.values()) {
            if (group.size() % 2 == 1) {
                netOps.add(group.get(group.size() - 1));
            }
        }
        return netOps;
    }

    /**
     * 把抵消后的净操作落库：批量增删 tb_thumb → 清待落库标记。
     * 增删都幂等，整批重投不会重复写。
     */
    private void applyBatch(List<ThumbMessage> netOps) {
        List<Thumb> toInsert = new ArrayList<>();
        List<Thumb> toDelete = new ArrayList<>();
        for (ThumbMessage message : netOps) {
            String action = message.getAction();
            if (ThumbConstant.MQ_UNLIKE_TAG.equals(action)) {
                toDelete.add(toThumb(message));
            } else if (ThumbConstant.MQ_TAG.equals(action)) {
                toInsert.add(toThumb(message));
            } else {
                log.error("未知的点赞消息 action，已丢弃，action={}，userId={}，postId={}",
                        action, message.getUserId(), message.getPostId());
            }
        }
        if (!toInsert.isEmpty()) {
            thumbMapper.batchInsertIgnore(toInsert);
        }
        if (!toDelete.isEmpty()) {
            thumbMapper.batchDeleteByUserAndPost(toDelete);
        }

        // 净生效的记录，清掉待落库标记
        for (ThumbMessage message : netOps) {
            thumbCache.clearUnsynced(message.getUserId(), message.getPostId());
        }
        log.info("点赞批量落库完成，净操作 {} 条", netOps.size());
    }

    /** MQ 消息 → tb_thumb 实体（插入用得上 createTime，删除用不上） */
    private Thumb toThumb(ThumbMessage message) {
        Thumb thumb = new Thumb();
        thumb.setId(message.getId());
        thumb.setUserId(message.getUserId());
        thumb.setPostId(message.getPostId());
        thumb.setCreateTime(toCreateTime(message.getThumbTime()));
        return thumb;
    }

    /** 消息里的毫秒时间戳转 LocalDateTime；缺失就取当前时间 */
    private LocalDateTime toCreateTime(Long thumbTime) {
        if (thumbTime == null) {
            return LocalDateTime.now(clock);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(thumbTime), clock.getZone());
    }

    /** 关停：先叫停工作线程，再关消费者（关消费者会把阻塞中的 poll 解开） */
    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(worker)) {
            worker.interrupt();
        }
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();
            } catch (Exception e) {
                log.error("关闭点赞消费者失败", e);
            }
        }
    }
}
