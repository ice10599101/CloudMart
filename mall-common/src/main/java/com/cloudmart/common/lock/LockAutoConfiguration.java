package com.cloudmart.common.lock;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 通用分布式锁自动配置。
 *
 * <p>仅当 classpath 存在 {@link RedissonClient} 且容器中已有 {@link RedissonClient} Bean 时生效，
 * 避免在不使用 Redisson 的模块中创建无用 Bean。
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnBean(RedissonClient.class)
public class LockAutoConfiguration {

    @Bean
    public LockFactory lockFactory(RedissonClient redissonClient) {
        return new LockFactory(redissonClient);
    }

    @Bean
    public LockFailureHandler lockFailureHandler() {
        return new DefaultLockFailureHandler();
    }

    @Bean
    public LockAspect lockAspect(LockFactory lockFactory, LockFailureHandler lockFailureHandler) {
        return new LockAspect(lockFactory, lockFailureHandler);
    }
}
