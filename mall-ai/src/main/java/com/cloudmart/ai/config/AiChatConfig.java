package com.cloudmart.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * AI 模块配置：对话历史 Redis 存储、Embedding 模型等。
 */
@Configuration
public class AiChatConfig {

    @Bean
    public StringRedisTemplate aiRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
