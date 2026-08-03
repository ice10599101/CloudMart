package com.cloudmart.product.config;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "redisson.enabled", havingValue = "true", matchIfMissing = true)
public class CacheBreakdownGuard {

    private static final Logger log = LoggerFactory.getLogger(CacheBreakdownGuard.class);
    private static final String LOCK_PREFIX = "product:lock:";
    private static final long WAIT_TIME_SECONDS = 3;
    private static final long LEASE_TIME_SECONDS = 10;

    private final RedissonClient redissonClient;

    public CacheBreakdownGuard(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 使用分布式锁防止缓存击穿：当热点 Key 缓存失效时，只允许一个线程重建缓存，
     * 其他线程等待后从缓存读取。
     */
    public <T> T getWithLock(String cacheKey, Supplier<T> cacheLoader) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + cacheKey);
        try {
            boolean acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (acquired) {
                try {
                    return cacheLoader.get();
                } finally {
                    lock.unlock();
                }
            } else {
                log.warn("Failed to acquire lock for cacheKey={}, returning null to avoid DB overload", cacheKey);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for cacheKey={}", cacheKey);
            return null;
        }
    }
}
