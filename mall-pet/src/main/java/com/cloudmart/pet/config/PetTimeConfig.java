package com.cloudmart.pet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 业务时钟装配（B04/B07）：所有服务端时间点从可注入 {@link Clock} 读取，
 * 测试注入固定 Clock，不依赖真实等待，也不修改机器系统时间。
 */
@Configuration
public class PetTimeConfig {

    // Bean 名不能叫 petClock：@Component PetClock 已按类名注册同名 Bean，这里只提供底层 java.time.Clock
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
