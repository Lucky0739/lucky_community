package com.lucky.gateway;

import com.lucky.common.net.LoopbackProxyBypass;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一入口
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        LoopbackProxyBypass.enable();
        SpringApplication.run(GatewayApplication.class, args);
    }
}
