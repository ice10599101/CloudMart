package com.cloudmart.wish.config;

import com.cloudmart.wish.repository.WishInviteCallbackOutboxMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T18 邀请资格回调 outbox 可观测指标。
 *
 * <p>积压类指标由定时任务刷新为本地快照 Gauge（抓取不查库，数据库短暂不可用时保留上次值）；
 * 交付结果按 settled/retry/terminal 累计 Counter，重试率 = (retry + terminal) / 全部尝试。</p>
 */
@Slf4j
@Component
public class WishInviteCallbackMetrics {

    public static final String PENDING = "wish_invite_callback_pending";
    public static final String DUE = "wish_invite_callback_due";
    public static final String OLDEST_PENDING_SECONDS = "wish_invite_callback_oldest_pending_seconds";
    public static final String FAILED = "wish_invite_callback_failed";
    public static final String ATTEMPT = "wish_invite_callback_attempt";
    public static final String RETRY_RATIO = "wish_invite_callback_retry_ratio";

    private final WishInviteCallbackOutboxMapper outboxMapper;
    private final Clock clock;

    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong due = new AtomicLong();
    private final AtomicLong oldestPendingSeconds = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private final Counter settled;
    private final Counter retry;
    private final Counter terminal;

    public WishInviteCallbackMetrics(MeterRegistry registry,
                                     WishInviteCallbackOutboxMapper outboxMapper,
                                     Clock clock) {
        this.outboxMapper = outboxMapper;
        this.clock = clock;
        Gauge.builder(PENDING, pending, AtomicLong::doubleValue)
                .description("待交付邀请资格回调总量（含退避中）").register(registry);
        Gauge.builder(DUE, due, AtomicLong::doubleValue)
                .description("到期且可立即执行的邀请资格回调积压").register(registry);
        Gauge.builder(OLDEST_PENDING_SECONDS, oldestPendingSeconds, AtomicLong::doubleValue)
                .description("最老待交付邀请资格回调已等待秒数").register(registry);
        Gauge.builder(FAILED, failed, AtomicLong::doubleValue)
                .description("达重试上限、待人工处置的邀请资格回调数").register(registry);
        Gauge.builder(RETRY_RATIO, this, WishInviteCallbackMetrics::computeRetryRatio)
                .description("邀请资格回调交付尝试中失败（含终止）占比").register(registry);
        this.settled = attemptCounter(registry, "settled");
        this.retry = attemptCounter(registry, "retry");
        this.terminal = attemptCounter(registry, "terminal");
    }

    private static Counter attemptCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(ATTEMPT).tag("outcome", outcome)
                .description("邀请资格回调交付尝试结果").register(registry);
    }

    /** 定时刷新积压快照；失败保留上次值，不让指标抓取打挂业务库 */
    @Scheduled(fixedDelayString = "${wish.invite-callback.metrics-refresh-ms:60000}")
    public void refresh() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            pending.set(outboxMapper.countPending());
            due.set(outboxMapper.countDuePending(now));
            failed.set(outboxMapper.countFailed());
            LocalDateTime oldest = outboxMapper.oldestPendingCreatedAt();
            oldestPendingSeconds.set(oldest == null ? 0
                    : Math.max(0, Duration.between(oldest, now).getSeconds()));
        } catch (Exception e) {
            log.warn("邀请回调指标刷新失败（保留上次快照）: {}", e.getMessage());
        }
    }

    public void recordSettled() {
        settled.increment();
    }

    public void recordRetry() {
        retry.increment();
    }

    public void recordTerminal() {
        terminal.increment();
    }

    /** 累计重试率（0~1）；无尝试时返回 0 */
    double computeRetryRatio() {
        double failedAttempts = retry.count() + terminal.count();
        double total = failedAttempts + settled.count();
        return total <= 0 ? 0 : failedAttempts / total;
    }
}
