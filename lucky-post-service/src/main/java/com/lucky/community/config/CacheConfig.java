package com.lucky.community.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lucky.community.cache.PostDetailCacheEntry;
import com.lucky.community.vo.PostSummaryVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${post.cache.hard-ttl-seconds:200}")
    private long hardTtlSeconds;

    @Value("${post.cache.local-max-size:10000}")
    private long localMaxSize;

    @Value("${post.cache.rebuild-pool-size:2}")
    private int rebuildPoolSize;

    @Value("${post.cache.image-query-pool-size:4}")
    private int imageQueryPoolSize;

    @Value("${post.cache.image-query-queue-capacity:64}")
    private int imageQueryQueueCapacity;

    @Value("${post.cache.feed-ttl-seconds:2}")
    private long feedTtlSeconds;

    @Value("${post.cache.feed-cache-max-size:500}")
    private long feedCacheMaxSize;

    @Bean("cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean("postDetailLocalCache")
    public Cache<Long, PostDetailCacheEntry> postDetailLocalCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(hardTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(localMaxSize)
                .recordStats()
                .build();
    }

    @Bean("postFeedLocalCache")
    public Cache<String, List<PostSummaryVO>> postFeedLocalCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(feedTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(feedCacheMaxSize)
                .recordStats()
                .build();
    }

    @Bean("cacheRebuildExecutor")
    public ThreadPoolTaskExecutor cacheRebuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(rebuildPoolSize);
        executor.setMaxPoolSize(rebuildPoolSize * 2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("post-cache-rebuild-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("postImageQueryExecutor")
    public ThreadPoolTaskExecutor postImageQueryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(imageQueryPoolSize);
        executor.setMaxPoolSize(imageQueryPoolSize);
        executor.setQueueCapacity(imageQueryQueueCapacity);
        executor.setThreadNamePrefix("post-image-query-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
