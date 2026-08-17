package com.cloudmart.wish.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 *
 * <p>Key 使用 String 序列化（便于 redis-cli 排查），Value 使用 JSON 序列化。
 * 心愿宇宙模块 Redis 使用场景：</p>
 * <ul>
 *   <li>分类字典缓存：{@code wish:categories}（TTL 1h + 抖动）</li>
 *   <li>今日推荐 ZSet：{@code wish:hot:feed}（TTL 10min + 抖动 0-60s）</li>
 *   <li>用户限频计数器：{@code wish:ratelimit:{userId}:{type}:{date}}（TTL 至当日 23:59）</li>
 *   <li>心愿详情缓存：{@code wish:detail:{id}}（TTL 5min + 抖动）</li>
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
