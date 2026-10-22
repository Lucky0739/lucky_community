package com.lucky.community.config;

import com.lucky.bloom.LongBloomFilter;
import com.lucky.community.mapper.PostMapper;
import com.lucky.redis.RedisKeyManage;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PostBloomFilterConfig {

    @Value("${post.bloom.expected-insertions:1000000}")
    private long expectedInsertions;

    @Value("${post.bloom.false-probability:0.01}")
    private double falseProbability;

    @Bean(initMethod = "initAndWarmUp")
    public LongBloomFilter postBloomFilter(RedissonClient redissonClient, PostMapper postMapper) {
        return new LongBloomFilter(
                redissonClient,
                RedisKeyManage.POST_BLOOM.key(),
                expectedInsertions,
                falseProbability,
                postMapper::selectAllIds);
    }

    /**
     * 布隆过滤器重建专用执行器：单线程、不排队。
     * 重建要全表加载 ID，耗时以分钟计，不能占用 Spring 默认的单线程调度器。
     * 队列容量 0 意味着上一次没跑完时再次触发会被拒绝，由任务自己记录并跳过。
     */
    @Bean("postBloomRebuildExecutor")
    public ThreadPoolTaskExecutor postBloomRebuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("post-bloom-rebuild-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
