package com.lucky.community.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时间源
 *
 * <p>需要手动取「当前时刻」的地方注入 {@link Clock}，不要在业务代码里直接调
 * {@code LocalDateTime.now()}：
 * <ul>
 *   <li>直接调的那个用的是 JVM 默认时区，且<b>不可替换</b>——单测里没法断言写进去的时间</li>
 *   <li>注入后可以用 {@code Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)}
 *       把时间钉死，断言就成立了</li>
 *   <li>将来要统一换时区（比如全站改 UTC），只改这一处</li>
 * </ul>
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
