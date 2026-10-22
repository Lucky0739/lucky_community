package com.lucky.community;

import com.lucky.common.net.LoopbackProxyBypass;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

// 用户服务的 UserFeignApi、KV 服务的 KeyValueFeignApi 都在别的包下，不在本服务的包里。
// 不显式列出来的话，@EnableFeignClients 只扫注解所在包，那些客户端不会被注册。
@EnableFeignClients(basePackages = {
        "com.lucky.community", "com.lucky.user.api", "com.lucky.kv.api"})
@SpringBootApplication(scanBasePackages = "com.lucky.community")
@MapperScan("com.lucky.community.**.mapper")
// AI 审核失败帖的定时重试任务（AiModerationRetryService）靠它注册
@EnableScheduling
// PostIndexMqSender 的 @Retryable / @Recover 靠它生效
@EnableRetry
public class PostServiceApplication {

    public static void main(String[] args) {
        LoopbackProxyBypass.enable();
        SpringApplication.run(PostServiceApplication.class, args);
    }
}
