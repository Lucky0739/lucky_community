package com.lucky.core.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class Healthy {
    @GetMapping("/health")
    public String health(){
        return "success";
    }
}
