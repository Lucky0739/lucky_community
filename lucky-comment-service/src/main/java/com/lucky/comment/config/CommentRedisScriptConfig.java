package com.lucky.comment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 评论计数用的 Lua 脚本。加减带 EXISTS 守卫和下限，避免把未缓存的计数从 0 建起来。
 */
@Configuration
public class CommentRedisScriptConfig {

    @Bean("commentCountScript")
    public DefaultRedisScript<Long> commentCountScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/comment_count_apply.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
