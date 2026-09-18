package com.cloudmart.pet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置（与 mall-wish 同构）。
 *
 * <p>Key 使用 String 序列化（便于 redis-cli 排查），Value 使用 JSON 序列化。
 * 社区宠物模块 Redis 使用场景（Key 规范 {service}:{env}:{module}:{type}:{id}）：</p>
 * <ul>
 *   <li>聊天/主动消息日限频：{@code pet:ratelimit:{userId}:{type}:{date}}（TTL 至当日 UTC 23:59）</li>
 *   <li>喂食日次数：{@code pet:feed:{userId}:{date}}（TTL 至当日 UTC 23:59）</li>
 *   <li>主动消息最小间隔：{@code pet:proactive:last:{userId}}（TTL min-interval-seconds）</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
