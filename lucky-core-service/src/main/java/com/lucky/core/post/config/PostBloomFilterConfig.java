package com.lucky.core.post.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lucky.bloom.LongBloomFilter;
import com.lucky.redis.RedisKeyManage;
import com.lucky.core.post.entity.Post;
import com.lucky.core.post.mapper.PostMapper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 帖子ID的布隆过滤器装配
 */
@Configuration
public class PostBloomFilterConfig {

    @Value("${post.bloom.expected-insertions:1000000}")
    private long expectedInsertions;

    @Value("${post.bloom.false-probability:0.01}")
    private double falseProbability;

    /**
     * 帖子ID布隆过滤器；随容器启动自动初始化并按需预热
     */
    @Bean(initMethod = "initAndWarmUp")
    public LongBloomFilter postBloomFilter(RedissonClient redissonClient, PostMapper postMapper) {
        return new LongBloomFilter(
                redissonClient,
                RedisKeyManage.POST_BLOOM.key(),
                expectedInsertions,
                falseProbability,
                () -> postMapper.selectList(new LambdaQueryWrapper<Post>().select(Post::getId))
                        .stream()
                        .map(Post::getId)
                        .toList());
    }
}
