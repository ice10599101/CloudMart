package com.cloudmart.common.lock;

import com.cloudmart.common.annotation.Lock;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/**
 * 默认分布式锁获取失败处理器，统一处理三种策略。
 *
 * <p>用单一实现替代三个策略类，避免类爆炸。如果未来需要自定义策略
 *（如记录监控指标、降级到本地锁等），可实现 {@link LockFailureHandler} 覆盖。
 */
public class DefaultLockFailureHandler implements LockFailureHandler {

    @Override
    public boolean acquire(RLock lock, Lock annotation) throws InterruptedException {
        long waitTime = annotation.waitTime();
        long leaseTime = annotation.leaseTime();
        boolean useWatchdog = leaseTime < 0;

        return switch (annotation.failureStrategy()) {
            case FAST_FAIL -> acquireFastFail(lock, leaseTime, useWatchdog);
            case RETRY_TIMEOUT -> acquireRetryTimeout(lock, waitTime, leaseTime, useWatchdog);
            case KEEP_RETRY -> {
                acquireKeepRetry(lock, leaseTime, useWatchdog);
                yield true;
            }
        };
    }

    /**
     * 快速失败：不等待，tryLock 立即返回。
     * <p>useWatchdog=true 时用 {@code tryLock()} 启用看门狗；
     * useWatchdog=false 时用 {@code tryLock(0, leaseTime, SECONDS)} 固定租约。
     */
    private boolean acquireFastFail(RLock lock, long leaseTime, boolean useWatchdog) throws InterruptedException {
        if (useWatchdog) {
            return lock.tryLock(0, TimeUnit.MILLISECONDS);
        }
        return lock.tryLock(0, leaseTime, TimeUnit.SECONDS);
    }

    /**
     * 超时重试：在 waitTime 秒内尝试获取锁。
     */
    private boolean acquireRetryTimeout(RLock lock, long waitTime, long leaseTime, boolean useWatchdog) throws InterruptedException {
        if (useWatchdog) {
            return lock.tryLock(waitTime, TimeUnit.SECONDS);
        }
        return lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
    }

    /**
     * 无限重试：阻塞直到获取锁。
     * <p>慎用：如果持有锁的线程异常未释放，当前线程会一直阻塞。
     */
    private void acquireKeepRetry(RLock lock, long leaseTime, boolean useWatchdog) {
        if (useWatchdog) {
            lock.lock();
        } else {
            lock.lock(leaseTime, TimeUnit.SECONDS);
        }
    }

}
