package com.cloudmart.wish;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 心愿宇宙微服务启动类。
 *
 * <p>独立 mall-wish 微服务，负责心愿宇宙领域事实与编排：
 * 许愿、互动、打卡、成长记录、还愿、星光、成就等。
 * 用户/文件/通知/评论等基础能力复用现有微服务（Feign 调用）。</p>
 */
@SpringBootApplication
@MapperScan("com.cloudmart.wish.repository")
@EnableFeignClients(basePackages = "com.cloudmart.wish.feign")
@EnableScheduling
@Import(GlobalExceptionHandler.class)
public class WishApplication {

    public static void main(String[] args) {
        SpringApplication.run(WishApplication.class, args);
    }
}
