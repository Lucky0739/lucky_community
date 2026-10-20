package com.lucky.interaction;

import com.lucky.common.net.LoopbackProxyBypass;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients
@EnableScheduling
@MapperScan("com.lucky.interaction.mapper")
@SpringBootApplication
public class InteractionServiceApplication {

    public static void main(String[] args) {
        LoopbackProxyBypass.enable();
        SpringApplication.run(InteractionServiceApplication.class, args);
    }
}
