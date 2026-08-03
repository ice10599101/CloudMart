package com.cloudmart.common.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 分布式锁工厂，根据 {@link LockType} 创建对应的 Redisson 锁实例。
 *
 * <p>使用工厂模式封装不同锁类型的创建逻辑，切面只需调用 {@link #create} 即可。
 */
public class LockFactory {

    private final RedissonClient redissonClient;

    public LockFactory(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 根据锁类型创建对应的 RLock 实例。
     *
     * @param name 锁名称（完整的 Redis key）
     * @param type 锁类型
     * @return Redisson 锁实例
     */
    public RLock create(String name, LockType type) {
        return switch (type) {
            case REENTRANT -> redissonClient.getLock(name);
            case FAIR -> redissonClient.getFairLock(name);
            case READ -> redissonClient.getReadWriteLock(name).readLock();
            case WRITE -> redissonClient.getReadWriteLock(name).writeLock();
        };
    }
}
