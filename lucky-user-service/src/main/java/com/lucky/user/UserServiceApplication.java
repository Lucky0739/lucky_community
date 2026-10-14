package com.lucky.user;

import com.lucky.common.net.LoopbackProxyBypass;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.lucky.user.mapper")
@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        LoopbackProxyBypass.enable();
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
