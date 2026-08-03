package com.cloudmart.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
@AutoConfigureAfter(DataRedisAutoConfiguration.class)
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    public RedisKeyspaceNotifier redisKeyspaceNotifier(RedisConnectionFactory connectionFactory) {
        return new RedisKeyspaceNotifier(connectionFactory);
    }

    static class RedisKeyspaceNotifier {

        RedisKeyspaceNotifier(RedisConnectionFactory connectionFactory) {
            try (var conn = connectionFactory.getConnection()) {
                conn.serverCommands().setConfig("notify-keyspace-events", "Ex");
                log.info("Redis keyspace notifications enabled (Ex)");
            } catch (Exception e) {
                log.warn("无法配置Redis keyspace notifications，超时取消将依赖数据库轮询兜底: {}", e.getMessage());
            }
        }
    }
}
