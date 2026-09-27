package com.cloudmart.common.async.outbox;

import com.cloudmart.common.async.EventEnvelope;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Outbox 投递策略（ASYNC-01）：退避计算与常量集中管理，便于测试。
 *
 * <p>重试策略：指数退避 + 抖动，默认 1 秒起步、5 分钟封顶、最多 12 次；
 * 超限转 DEAD_LETTER（工作台人工重试 + 告警）。</p>
 */
public final class OutboxRetryPolicy {

    public static final int DEFAULT_MAX_ATTEMPTS = 12;
    public static final long DEFAULT_BASE_BACKOFF_MILLIS = 1_000L;
    public static final long DEFAULT_MAX_BACKOFF_MILLIS = 5 * 60_000L;

    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;

    public OutboxRetryPolicy(int maxAttempts, long baseBackoffMillis, long maxBackoffMillis) {
        this.maxAttempts = maxAttempts;
        this.baseBackoffMillis = baseBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    public static OutboxRetryPolicy defaults() {
        return new OutboxRetryPolicy(DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_BACKOFF_MILLIS, DEFAULT_MAX_BACKOFF_MILLIS);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * 第 {@code attempts} 次失败后的下次重试延迟：base * 2^attempts，封顶 max，加 [0, base) 抖动。
     */
    public long nextBackoffMillis(int attempts) {
        long exponential = baseBackoffMillis * (1L << Math.min(attempts, 20));
        long capped = Math.min(exponential, maxBackoffMillis);
        long jitter = baseBackoffMillis <= 1
                ? 0
                : ThreadLocalRandom.current().nextLong(0, baseBackoffMillis);
        return capped + jitter;
    }

    /** 供测试注入确定性时钟的场景计算绝对重试时刻 */
    public java.time.LocalDateTime nextRetryAt(int attempts, Clock clock) {
        return java.time.LocalDateTime.ofInstant(clock.instant().plusMillis(nextBackoffMillis(attempts)),
                clock.getZone());
    }
}
