package com.lucky.search;

import com.lucky.common.net.LoopbackProxyBypass;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SearchServiceApplication {

    public static void main(String[] args) {
        LoopbackProxyBypass.enable();
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
