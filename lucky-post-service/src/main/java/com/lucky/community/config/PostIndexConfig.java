package com.lucky.community.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PostIndexConfig {

    /**
     * 索引全量重建专用执行器。
     *
     * <p><b>单线程 + 队列容量 1</b>：同一时刻只允许一轮重建，第二次触发直接拒绝 ——
     * 重建是"扫全表 + 逐条投递"，并发跑只会重复投递、放大 MQ 和 Cassandra 的压力。
     * 用独立线程池而不是复用审核线程池：重建会长时间占住线程，不能拖住发帖链路。
     */
    @Bean("postIndexRebuildExecutor")
    public Executor postIndexRebuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("post-index-rebuild-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
