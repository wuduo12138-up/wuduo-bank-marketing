package com.wuduo.bank.point;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point service boot application
 */
@SpringBootApplication(scanBasePackages = {"com.wuduo.bank.point", "com.wuduo.bank.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.wuduo.bank.point.api.client")
@EnableScheduling
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
