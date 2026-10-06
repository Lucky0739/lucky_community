package com.lucky.interaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class FollowRedisConfig {

    @Bean("followScript")
    public DefaultRedisScript<Long> followScript() {
        return longScript("lua/follow.lua");
    }

    @Bean("followRollbackScript")
    public DefaultRedisScript<Long> followRollbackScript() {
        return longScript("lua/follow_rollback.lua");
    }

    @Bean("unfollowScript")
    public DefaultRedisScript<Long> unfollowScript() {
        return longScript("lua/unfollow.lua");
    }

    @Bean("unfollowRollbackScript")
    public DefaultRedisScript<Long> unfollowRollbackScript() {
        return longScript("lua/unfollow_rollback.lua");
    }

    @Bean("followStatScript")
    public DefaultRedisScript<Long> followStatScript() {
        return longScript("lua/follow_stat.lua");
    }

    @Bean("followStatDrainScript")
    @SuppressWarnings("rawtypes")
    public DefaultRedisScript<List> followStatDrainScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/follow_stat_drain.lua"));
        script.setResultType(List.class);
        return script;
    }

    private DefaultRedisScript<Long> longScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
