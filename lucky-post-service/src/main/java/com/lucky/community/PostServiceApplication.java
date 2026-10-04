package com.lucky.community;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.lucky.community")
@MapperScan("com.lucky.community.**.mapper")
public class PostServiceApplication {

    public static void main(String[] args) {
        bypassProxyForLoopback();
        SpringApplication.run(PostServiceApplication.class, args);
    }

    private static void bypassProxyForLoopback() {
        String loopbackHosts = "localhost|127.*|[::1]";
        appendNonProxyHosts("socksNonProxyHosts", loopbackHosts);
        appendNonProxyHosts("http.nonProxyHosts", loopbackHosts);
    }

    private static void appendNonProxyHosts(String propertyName, String loopbackHosts) {
        String configuredHosts = System.getProperty(propertyName, "");
        if (configuredHosts.isBlank()) {
            System.setProperty(propertyName, loopbackHosts);
            return;
        }
        if (!configuredHosts.contains("127.*")) {
            System.setProperty(propertyName, configuredHosts + "|" + loopbackHosts);
        }
    }
}
