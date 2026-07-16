package com.wuduo.bank.rights;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Rights Service Boot Application
 */
@SpringBootApplication(scanBasePackages = {"com.wuduo.bank.rights", "com.wuduo.bank.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.wuduo.bank.rights.api.client")
@EnableScheduling
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
