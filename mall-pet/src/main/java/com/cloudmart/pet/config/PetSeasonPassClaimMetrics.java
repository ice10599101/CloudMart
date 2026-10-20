package com.cloudmart.pet.config;

import com.cloudmart.pet.repository.PetSeasonPassClaimMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T18 通行证补发可观测指标。
 *
 * <p>积压类指标由定时任务从 claim 事实表刷新到本地快照后暴露为 Gauge（抓取时不再查库，
 * 数据库短暂不可用时保留上次值）；交付结果按 settled/retry/terminal 三类累计 Counter，
 * 重试率 = (retry + terminal) / 全部尝试，供告警与运营看板使用。</p>
 */
@Slf4j
@Component
public class PetSeasonPassClaimMetrics {

    public static final String PENDING = "pet_season_pass_claim_pending";
    public static final String DUE = "pet_season_pass_claim_due";
    public static final String OLDEST_PENDING_SECONDS = "pet_season_pass_claim_oldest_pending_seconds";
    public static final String NEEDS_ATTENTION = "pet_season_pass_claim_needs_attention";
    public static final String ATTEMPT = "pet_season_pass_claim_attempt";
    public static final String MANUAL_RETRY = "pet_season_pass_claim_manual_retry";
    public static final String RETRY_RATIO = "pet_season_pass_claim_retry_ratio";

    private final PetSeasonPassClaimMapper claimMapper;
    private final PetClock petClock;

    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong due = new AtomicLong();
    private final AtomicLong oldestPendingSeconds = new AtomicLong();
    private final AtomicLong needsAttention = new AtomicLong();

    private final Counter settled;
    private final Counter retry;
    private final Counter terminal;
    private final Counter manualRetry;

    public PetSeasonPassClaimMetrics(MeterRegistry registry, PetSeasonPassClaimMapper claimMapper,
                                     PetClock petClock) {
        this.claimMapper = claimMapper;
        this.petClock = petClock;
        Gauge.builder(PENDING, pending, AtomicLong::doubleValue)
                .description("待补发领取事实总量（含退避与租约中）").register(registry);
        Gauge.builder(DUE, due, AtomicLong::doubleValue)
                .description("到期且可立即执行的待补发积压").register(registry);
        Gauge.builder(OLDEST_PENDING_SECONDS, oldestPendingSeconds, AtomicLong::doubleValue)
                .description("最老待补发事实已等待秒数").register(registry);
        Gauge.builder(NEEDS_ATTENTION, needsAttention, AtomicLong::doubleValue)
                .description("达重试上限或不可重试、待人工处置的事实数").register(registry);
        Gauge.builder(RETRY_RATIO, this, PetSeasonPassClaimMetrics::computeRetryRatio)
                .description("交付尝试中失败（含终止）占比").register(registry);
        this.settled = attemptCounter(registry, "settled");
        this.retry = attemptCounter(registry, "retry");
        this.terminal = attemptCounter(registry, "terminal");
        this.manualRetry = Counter.builder(MANUAL_RETRY)
                .description("人工重试（重放原事实）次数").register(registry);
    }

    private static Counter attemptCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(ATTEMPT).tag("outcome", outcome)
                .description("通行证交付尝试结果").register(registry);
    }

    /** 定时刷新积压快照；失败保留上次值，不让指标抓取打挂业务库 */
    @Scheduled(fixedDelayString = "${pet.season-pass.metrics-refresh-ms:60000}")
    public void refresh() {
        try {
            LocalDateTime now = petClock.nowUtc();
            pending.set(claimMapper.countPending());
            due.set(claimMapper.countDuePending(now));
            needsAttention.set(claimMapper.countNeedsAttention());
            LocalDateTime oldest = claimMapper.oldestPendingCreatedAt();
            oldestPendingSeconds.set(oldest == null ? 0
                    : Math.max(0, Duration.between(oldest, now).getSeconds()));
        } catch (Exception e) {
            log.warn("通行证补发指标刷新失败（保留上次快照）: {}", e.getMessage());
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

    public void recordManualRetry() {
        manualRetry.increment();
    }

    /** 累计重试率（0~1）；无尝试时返回 0 */
    double computeRetryRatio() {
        double failed = retry.count() + terminal.count();
        double total = failed + settled.count();
        return total <= 0 ? 0 : failed / total;
    }
}
