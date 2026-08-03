package com.cloudmart.common.lock;

/**
 * 分布式锁类型枚举。
 *
 * <p>对应 Redisson 提供的四种锁实现：
 * <ul>
 *   <li>{@link #REENTRANT} — 可重入锁（默认），支持同线程多次获取</li>
 *   <li>{@link #FAIR} — 公平锁，按请求顺序获取，避免饥饿</li>
 *   <li>{@link #READ} — 读写锁的读锁，允许多读并发</li>
 *   <li>{@link #WRITE} — 读写锁的写锁，互斥排他</li>
 * </ul>
 */
public enum LockType {
    REENTRANT,
    FAIR,
    READ,
    WRITE
}
