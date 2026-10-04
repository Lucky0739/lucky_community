package com.lucky.community.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 审核链路的两块底层组件：线程池 + HTTP 客户端
 */
@Configuration
public class AiModerationConfig {

    /** Python 审核服务地址。默认回环：该服务没有鉴权，不该跨机暴露 */
    @Value("${ai.moderation.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    @Value("${ai.moderation.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    /**
     * 读超时。完整治理流程最坏预算 25s，额外留 5s 网络与序列化余量。
     */
    @Value("${ai.moderation.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Value("${ai.moderation.pool-size:2}")
    private int poolSize;

    @Value("${ai.moderation.queue-capacity:200}")
    private int queueCapacity;

    /**
     * 调 Python 审核服务的 HTTP 客户端
     */
    @Bean("aiModerationRestClient")
    public RestClient aiModerationRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * 执行审核任务的线程池
     */
    @Bean("aiModerationExecutor")
    public ThreadPoolTaskExecutor aiModerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize * 2);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-moderation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
