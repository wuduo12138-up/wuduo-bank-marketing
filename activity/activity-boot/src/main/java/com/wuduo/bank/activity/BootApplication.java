package com.wuduo.bank.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activity service bootstrap application
 */
@SpringBootApplication(scanBasePackages = {"com.wuduo.bank.activity", "com.wuduo.bank.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "com.wuduo.bank.activity.api.client",
        "com.wuduo.bank.point.api.client"
})
@EnableScheduling
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
