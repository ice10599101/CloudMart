package com.cloudmart.common.lock;

import org.redisson.api.RLock;

import com.cloudmart.common.annotation.Lock;

/**
 * 分布式锁获取失败处理器（策略模式）。
 *
 * <p>不同 {@link LockFailureStrategy} 对应不同的处理实现，
 * 由 {@link com.cloudmart.common.lock.LockAspect} 调用。
 */
public interface LockFailureHandler {

    /**
     * 尝试获取锁，根据策略决定是否等待/重试。
     *
     * @param lock      Redisson 锁实例
     * @param annotation 锁注解配置（包含 waitTime、leaseTime 等）
     * @return {@code true} 获取成功，{@code false} 获取失败
     * @throws InterruptedException 等待期间被中断
     */
    boolean acquire(RLock lock, Lock annotation) throws InterruptedException;
}
