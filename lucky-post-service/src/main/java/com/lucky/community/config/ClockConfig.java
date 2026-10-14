package com.lucky.community.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时间源
 */
@Configuration
public class ClockConfig {

    /**
     * {@code systemDefaultZone()} 与 {@code LocalDateTime.now()} 的默认行为一致，
     * 即「JVM 默认时区」。换成固定时区只改这一行
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
