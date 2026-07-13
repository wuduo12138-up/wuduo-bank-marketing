package com.wuduo.bank.rights;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Rights Service Boot Application
 */
@SpringBootApplication(scanBasePackages = "com.wuduo.bank.rights")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.wuduo.bank.rights.api.client")
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
