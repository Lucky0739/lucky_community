package com.lucky.interaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

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

    private DefaultRedisScript<Long> longScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
