package com.lucky.community.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用 PostgreSQL WAL 逻辑解码订阅帖子变更，异步删 Redis。
 *
 * <h3>PostgreSQL 没有 binlog</h3>
 * MySQL 改表走 binlog，这边对应的是 WAL + {@code pgoutput} 逻辑复制。
 * 改帖 / 逻辑删帖都是 {@code tb_post} 的 UPDATE，会进 WAL；请求线程里 {@code DEL}
 * 失败只打日志，<b>位点只在 Redis 删除成功后前进</b>，Redis 恢复后会自动补删。
 *
 * <h3>和 afterCommit.evict 的关系</h3>
 * 请求路径仍尽力删一次，用户马上看到新内容。本类是补偿：那一次失败了，WAL 会再删。
 * 两次 DEL 幂等。
 *
 * <h3>多实例</h3>
 * 一个逻辑槽同时只能被一个连接消费。用 Redisson 锁选出唯一运行者，
 * 持锁实例挂了之后锁过期，别的实例接着同一个槽读。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "post.cache.cdc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PostWalCacheCdc implements SmartLifecycle {

    private static final String LEADER_LOCK = "post:cdc:leader";
    private static final String TABLE_POST = "tb_post";
    private static final String TABLE_POST_IMAGE = "tb_post_image";

    private final PostDetailCache postDetailCache;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    private final boolean enabled;
    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    private final String slotName;
    private final String publicationName;
    private final String offsetDir;
    private final long retryMaxBackoffMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private volatile Thread worker;
    private volatile DebeziumEngine<ChangeEvent<String, String>> engine;
    private volatile RLock leaderLock;

    public PostWalCacheCdc(PostDetailCache postDetailCache,
                           @Qualifier("cacheObjectMapper") ObjectMapper objectMapper,
                           RedissonClient redissonClient,
                           @Value("${post.cache.cdc.enabled:true}") boolean enabled,
                           @Value("${spring.datasource.url}") String jdbcUrl,
                           @Value("${spring.datasource.username}") String jdbcUser,
                           @Value("${spring.datasource.password}") String jdbcPassword,
                           @Value("${post.cache.cdc.slot-name:lucky_post_cache}") String slotName,
                           @Value("${post.cache.cdc.publication-name:lucky_post_cache}") String publicationName,
                           @Value("${post.cache.cdc.offset-dir:${java.io.tmpdir}/lucky-post-cdc}") String offsetDir,
                           @Value("${post.cache.cdc.retry-max-backoff-ms:30000}") long retryMaxBackoffMs) {
        this.postDetailCache = postDetailCache;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.enabled = enabled;
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
        this.slotName = slotName;
        this.publicationName = publicationName;
        this.offsetDir = offsetDir;
        this.retryMaxBackoffMs = retryMaxBackoffMs;
    }

    @Override
    public void start() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        stopped.set(false);
        Thread thread = new Thread(this::runLoop, "post-wal-cdc");
        this.worker = thread;
        thread.start();
        log.info("帖子 WAL CDC 已启动，slot={}，publication={}", slotName, publicationName);
    }

    @Override
    public void stop() {
        stopped.set(true);
        running.set(false);
        DebeziumEngine<ChangeEvent<String, String>> current = this.engine;
        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                log.warn("关闭 WAL CDC 引擎失败", e);
            }
        }
        Thread thread = this.worker;
        if (thread != null) {
            thread.interrupt();
        }
        unlockLeader();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** 晚于 Redis 连接启动，停得比 Redis 早，避免关库过程中还在 DEL */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 32;
    }

    private void runLoop() {
        long backoff = 5_000L;
        boolean loggedStack = false;
        while (!stopped.get()) {
            RLock lock = redissonClient.getLock(LEADER_LOCK);
            boolean locked = false;
            try {
                locked = lock.tryLock(3, TimeUnit.SECONDS);
                if (!locked) {
                    TimeUnit.MILLISECONDS.sleep(5_000);
                    continue;
                }
                this.leaderLock = lock;
                waitUntilWalLogical();
                if (stopped.get()) {
                    return;
                }
                runEngine();
                if (!stopped.get()) {
                    log.warn("WAL CDC 引擎已退出，{}ms 后重试。postgres 需要 wal_level=logical，改完后重启容器", backoff);
                    TimeUnit.MILLISECONDS.sleep(backoff);
                    backoff = Math.min(backoff * 2, retryMaxBackoffMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!stopped.get()) {
                    if (!loggedStack) {
                        log.warn("WAL CDC 异常，{}ms 后重试。postgres 需要 wal_level=logical，改完后重启容器", backoff, e);
                        loggedStack = true;
                    } else {
                        log.warn("WAL CDC 仍异常，{}ms 后重试：{}", backoff, e.toString());
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(backoff);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    backoff = Math.min(backoff * 2, retryMaxBackoffMs);
                }
            } finally {
                if (locked) {
                    unlockLeader();
                }
            }
        }
    }

    /**
     * Debezium 在 wal_level≠logical 时会 ERROR + 堆栈。先自己查，不够就静等，避免刷屏。
     */
    private void waitUntilWalLogical() throws InterruptedException {
        boolean warned = false;
        while (!stopped.get()) {
            String level = readWalLevel();
            if ("logical".equalsIgnoreCase(level)) {
                if (warned) {
                    log.info("postgres wal_level 已是 logical，开始订阅 WAL");
                }
                return;
            }
            if (!warned) {
                log.warn("postgres wal_level={}，CDC 暂停。请执行: docker compose up -d --force-recreate postgres", level);
                warned = true;
            }
            TimeUnit.SECONDS.sleep(30);
        }
    }

    private String readWalLevel() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW wal_level")) {
            return resultSet.next() ? resultSet.getString(1) : "unknown";
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private void runEngine() {
        File dir = new File(offsetDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("无法创建 CDC 位点目录，将继续尝试，dir={}", dir.getAbsolutePath());
        }
        PgEndpoint endpoint = PgEndpoint.parse(jdbcUrl);
        Properties props = new Properties();
        props.setProperty("name", "lucky-post-cache-cdc");
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", new File(dir, "offsets.dat").getAbsolutePath());
        props.setProperty("offset.flush.interval.ms", "10000");
        props.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        props.setProperty("schema.history.internal.file.filename", new File(dir, "schema.dat").getAbsolutePath());
        props.setProperty("topic.prefix", "lucky-post");
        props.setProperty("database.hostname", endpoint.host());
        props.setProperty("database.port", String.valueOf(endpoint.port()));
        props.setProperty("database.user", jdbcUser);
        props.setProperty("database.password", jdbcPassword);
        props.setProperty("database.dbname", endpoint.database());
        props.setProperty("plugin.name", "pgoutput");
        props.setProperty("slot.name", slotName);
        props.setProperty("publication.name", publicationName);
        props.setProperty("publication.autocreate.mode", "filtered");
        props.setProperty("slot.drop.on.stop", "false");
        props.setProperty("snapshot.mode", "no_data");
        props.setProperty("tombstones.on.delete", "false");
        props.setProperty("table.include.list", "community.tb_post,community.tb_post_image");
        props.setProperty("heartbeat.interval.ms", "15000");
        props.setProperty("max.batch.size", "32");

        try (DebeziumEngine<ChangeEvent<String, String>> built = DebeziumEngine.create(Json.class)
                .using(props)
                .notifying(this::handleBatch)
                .using((success, message, error) -> {
                    if (!success && !stopped.get()) {
                        log.warn("WAL CDC 引擎停止：{}", message);
                    }
                })
                .build()) {
            this.engine = built;
            built.run();
        } catch (Exception e) {
            this.engine = null;
            throw e instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(e);
        } finally {
            this.engine = null;
        }
    }

    private void handleBatch(List<ChangeEvent<String, String>> records,
                             DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer)
            throws InterruptedException {
        for (ChangeEvent<String, String> record : records) {
            if (stopped.get()) {
                throw new InterruptedException("WAL CDC 已停止");
            }
            Long postId = parsePostId(record);
            if (postId != null) {
                evictUntilSuccess(postId);
            }
            committer.markProcessed(record);
        }
        committer.markBatchFinished();
    }

    /**
     * Redis 删不掉就阻塞在这里，Debezium 不会提交这一批的位点。
     */
    private void evictUntilSuccess(Long postId) throws InterruptedException {
        long backoff = 200L;
        while (!stopped.get()) {
            try {
                postDetailCache.evictStrict(postId);
                return;
            } catch (Exception e) {
                log.warn("WAL CDC 删除帖子缓存失败，将重试，postId={}，backoffMs={}", postId, backoff, e);
                TimeUnit.MILLISECONDS.sleep(backoff);
                backoff = Math.min(backoff * 2, retryMaxBackoffMs);
            }
        }
        throw new InterruptedException("WAL CDC 已停止，缓存尚未删掉，postId=" + postId);
    }

    Long parsePostId(ChangeEvent<String, String> record) {
        String value = record == null ? null : record.value();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payload = root.hasNonNull("payload") ? root.get("payload") : root;
            String op = payload.path("op").asText("");
            if ("r".equals(op)) {
                return null;
            }
            String table = payload.path("source").path("table").asText("");
            JsonNode row = payload.get("after");
            if (row == null || row.isNull()) {
                row = payload.get("before");
            }
            if (row == null || row.isNull()) {
                return null;
            }
            if (TABLE_POST.equals(table)) {
                return longField(row, "id");
            }
            if (TABLE_POST_IMAGE.equals(table)) {
                return longField(row, "post_id");
            }
            return null;
        } catch (Exception e) {
            log.error("WAL CDC 事件无法解析，已跳过以免卡死复制槽，value={}", value, e);
            return null;
        }
    }

    private static Long longField(JsonNode row, String field) {
        JsonNode node = row.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.valueOf(node.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private void unlockLeader() {
        RLock lock = this.leaderLock;
        this.leaderLock = null;
        if (lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("释放 WAL CDC 领导锁失败，将由租期过期", e);
        }
    }

    record PgEndpoint(String host, int port, String database) {

        static PgEndpoint parse(String jdbcUrl) {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
                throw new IllegalArgumentException("无法解析数据源 URL：" + jdbcUrl);
            }
            String rest = jdbcUrl.substring("jdbc:".length());
            int query = rest.indexOf('?');
            URI uri = URI.create(query < 0 ? rest : rest.substring(0, query));
            String db = uri.getPath() == null ? "" : uri.getPath();
            if (db.startsWith("/")) {
                db = db.substring(1);
            }
            int slash = db.indexOf('/');
            if (slash >= 0) {
                db = db.substring(0, slash);
            }
            int port = uri.getPort() < 0 ? 5432 : uri.getPort();
            if (uri.getHost() == null || db.isBlank()) {
                throw new IllegalArgumentException("数据源 URL 缺少 host 或库名：" + jdbcUrl);
            }
            return new PgEndpoint(uri.getHost(), port, db);
        }
    }
}
