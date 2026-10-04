package com.lucky.community.config;

import com.lucky.common.bloom.LongBloomFilter;
import com.lucky.community.mapper.BarMapper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 贴吧 ID 布隆过滤器。Redisson 派生 key：位图 {@code bar:bloom}、配置、预热标记等。
 */
@Configuration
public class BarBloomFilterConfig {

    /** 与 {@code RedisKeyManage} 中 BAR_BLOOM 命名约定一致 */
    public static final String FILTER_NAME = "bar:bloom";

    @Value("${bar.bloom.expected-insertions:100000}")
    private long expectedInsertions;

    @Value("${bar.bloom.false-probability:0.01}")
    private double falseProbability;

    @Bean(initMethod = "initAndWarmUp")
    public LongBloomFilter barBloomFilter(RedissonClient redissonClient, BarMapper barMapper) {
        return new LongBloomFilter(
                redissonClient,
                FILTER_NAME,
                expectedInsertions,
                falseProbability,
                barMapper::selectAllIds);
    }
}
