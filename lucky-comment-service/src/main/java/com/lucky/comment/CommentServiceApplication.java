package com.lucky.comment;

import com.lucky.common.net.LoopbackProxyBypass;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableRetry
@EnableScheduling
@EnableFeignClients(basePackages = {"com.lucky.comment", "com.lucky.kv.api", "com.lucky.user.api"})
@MapperScan("com.lucky.comment.mapper")
@SpringBootApplication
public class CommentServiceApplication {

    public static void main(String[] args) {
        LoopbackProxyBypass.enable();
        SpringApplication.run(CommentServiceApplication.class, args);
    }
}
