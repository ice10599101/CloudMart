package com.cloudmart.payment;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@MapperScan({"com.cloudmart.payment.repository", "com.cloudmart.payment.reconciliation", "com.cloudmart.common.async.mapper"})
@EnableDiscoveryClient
@EnableFeignClients
@Import(GlobalExceptionHandler.class)
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
