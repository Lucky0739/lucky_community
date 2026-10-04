package com.lucky.community.config;

import com.lucky.bloom.LongBloomFilter;
import com.lucky.community.mapper.PostMapper;
import com.lucky.redis.RedisKeyManage;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
