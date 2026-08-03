package com.cloudmart.common.lock;

/**
 * 分布式锁获取失败时的处理策略。
 *
 * <ul>
 *   <li>{@link #FAST_FAIL} — 快速失败，不等待，获取不到立即抛异常（默认）</li>
 *   <li>{@link #RETRY_TIMEOUT} — 超时重试，在 {@code waitTime} 秒内重试获取</li>
 *   <li>{@link #KEEP_RETRY} — 无限重试，阻塞直到获取锁（慎用，可能死等）</li>
 * </ul>
 */
public enum LockFailureStrategy {
    FAST_FAIL,
    RETRY_TIMEOUT,
    KEEP_RETRY
}
