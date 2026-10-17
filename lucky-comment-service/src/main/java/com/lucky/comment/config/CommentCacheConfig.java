package com.lucky.comment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

/**
 * 评论总数回填 Redis 用的线程池。读路径不阻塞在缓存回填上。
 */
@Configuration
public class CommentCacheConfig {

    @Bean("commentCacheExecutor")
    public ThreadPoolTaskExecutor commentCacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("comment-cache-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
