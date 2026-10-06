package com.lucky.community.config;

import com.lucky.common.bloom.LongBloomFilter;
import com.lucky.community.mapper.BarMapper;
import jakarta.annotation.PreDestroy;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 贴吧 ID 布隆过滤器。连独立 Redis，不注册成容器里的 {@code RedissonClient}，
 * 避免和业务缓存那份客户端抢注入。
 */
@Configuration
public class BarBloomFilterConfig {

    public static final String FILTER_NAME = "bar:bloom";

    @Value("${bar.bloom.expected-insertions:100000}")
    private long expectedInsertions;

    @Value("${bar.bloom.false-probability:0.01}")
    private double falseProbability;

    @Value("${bar.bloom.false-negative-degrade-threshold:3}")
    private int falseNegativeDegradeThreshold;

    @Value("${bar.bloom.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${bar.bloom.redis.port:6381}")
    private int redisPort;

    @Value("${bar.bloom.redis.password:}")
    private String redisPassword;

    private RedissonClient bloomRedissonClient;

    @Bean(initMethod = "initAndWarmUp")
    public LongBloomFilter barBloomFilter(BarMapper barMapper) {
        this.bloomRedissonClient = createBloomClient();
        return new LongBloomFilter(
                bloomRedissonClient,
                FILTER_NAME,
                expectedInsertions,
                falseProbability,
                barMapper::selectAllIds,
                falseNegativeDegradeThreshold);
    }

    @PreDestroy
    public void shutdownBloomClient() {
        if (bloomRedissonClient != null) {
            bloomRedissonClient.shutdown();
        }
    }

    private RedissonClient createBloomClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        if (StringUtils.hasText(redisPassword)) {
            config.useSingleServer().setPassword(redisPassword);
        }
        return Redisson.create(config);
    }
}
