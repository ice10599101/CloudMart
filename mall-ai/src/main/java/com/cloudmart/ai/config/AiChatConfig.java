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

    /**
     * T26：模型调用显式超时——RestClient 默认无读超时，模型 hang 会拖死请求线程。
     * 连接 2s / 读取 30s（对话生成合理上界）；spring-ai 自动装配消费该 Builder。
     */
    @Bean
    public org.springframework.web.client.RestClient.Builder aiRestClientBuilder() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        return org.springframework.web.client.RestClient.builder()
                .requestFactory(factory);
    }
}
