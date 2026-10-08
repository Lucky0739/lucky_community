package com.lucky.community.config;

import com.lucky.common.bloom.LongBloomFilter;
import com.lucky.community.mapper.BarMapper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 贴吧 ID 布隆过滤器。和业务缓存共用 6380 那台 Redis，直接用容器里的 {@code RedissonClient}。
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

    @Bean(initMethod = "initAndWarmUp")
    public LongBloomFilter barBloomFilter(RedissonClient redissonClient, BarMapper barMapper) {
        return new LongBloomFilter(
                redissonClient,
                FILTER_NAME,
                expectedInsertions,
                falseProbability,
                barMapper::selectAllIds,
                falseNegativeDegradeThreshold);
    }
}
