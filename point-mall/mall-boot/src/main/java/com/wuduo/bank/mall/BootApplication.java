package com.wuduo.bank.mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point Mall Service Bootstrap Application
 */
@SpringBootApplication(scanBasePackages = {"com.wuduo.bank.mall", "com.wuduo.bank.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.wuduo.bank.mall.api.client",
                                     "com.wuduo.bank.point.api.client",
                                     "com.wuduo.bank.rights.api.client"})
@EnableScheduling
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
