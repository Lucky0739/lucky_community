package com.lucky.interaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class ThumbRedisConfig {

    @Bean("thumbLikeScript")
    public DefaultRedisScript<Long> thumbLikeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/thumb_like.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("thumbBloomInitScript")
    public DefaultRedisScript<Long> thumbBloomInitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/thumb_bloom_init.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("thumbUnlikeScript")
    public DefaultRedisScript<Long> thumbUnlikeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/thumb_unlike.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("thumbUnlikeRollbackScript")
    public DefaultRedisScript<Long> thumbUnlikeRollbackScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/thumb_unlike_rollback.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("thumbCountStatScript")
    public DefaultRedisScript<Long> thumbCountStatScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/thumb_count_stat.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("thumbCountDrainScript")
    @SuppressWarnings("unchecked")
    public DefaultRedisScript<List<String>> thumbCountDrainScript() {
        DefaultRedisScript<List<String>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/thumb_count_drain.lua"));
        script.setResultType((Class<List<String>>) (Class<?>) List.class);
        return script;
    }

    /** 点赞/收藏共用的刷库锁释放脚本 */
    @Bean("countFlushUnlockScript")
    public DefaultRedisScript<Long> countFlushUnlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/count_flush_unlock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
