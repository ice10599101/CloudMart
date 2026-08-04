package com.cloudmart.common.config;

import com.cloudmart.common.aspect.IdempotentAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 幂等性切面自动配置。
 *
 * <p>当 classpath 存在 {@link StringRedisTemplate} 时自动注册 {@link IdempotentAspect}，
 * 各微服务只需在 Controller 方法上标注 {@link com.cloudmart.common.annotation.Idempotent} 即可生效。</p>
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class IdempotentAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public IdempotentAspect idempotentAspect(StringRedisTemplate redisTemplate) {
        return new IdempotentAspect(redisTemplate);
    }
}
