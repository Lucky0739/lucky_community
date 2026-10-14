package com.lucky.kv;

import com.lucky.common.net.LoopbackProxyBypass;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

@SpringBootApplication
@EnableCassandraRepositories(basePackages = "com.lucky.kv.biz.repository")
public class KvServiceApplication {

    public static void main(String[] args) {
        LoopbackProxyBypass.enable();
        SpringApplication.run(KvServiceApplication.class, args);
    }
}
