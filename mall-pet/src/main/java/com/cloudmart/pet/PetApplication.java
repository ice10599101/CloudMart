package com.cloudmart.pet;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社区宠物模块启动类。
 *
 * <p>宠物是社区业务模块而非独立游戏：复用网关用户身份（X-User-Id）、mall-wish
 * 漂流瓶/星光（Feign 内部端点）、mall-notification 通知（pet-events MQ）、
 * mall-community 社区事件（community-events MQ），自身只拥有 mall_pet 库。</p>
 */
@SpringBootApplication
@MapperScan("com.cloudmart.pet.repository")
@EnableFeignClients(basePackages = "com.cloudmart.pet.feign")
@EnableScheduling
@Import(GlobalExceptionHandler.class)
public class PetApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetApplication.class, args);
    }
}
