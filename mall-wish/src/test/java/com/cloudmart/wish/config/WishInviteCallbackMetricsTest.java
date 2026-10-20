package com.cloudmart.wish.config;

import com.cloudmart.wish.repository.WishInviteCallbackOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T18：邀请资格回调积压/最老等待/重试率/终止数指标的采集口径。 */
class WishInviteCallbackMetricsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 10, 0);
    private static final Instant FIXED = NOW.toInstant(ZoneOffset.UTC);

    private WishInviteCallbackOutboxMapper outboxMapper;
    private SimpleMeterRegistry registry;
    private WishInviteCallbackMetrics metrics;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(WishInviteCallbackOutboxMapper.class);
        registry = new SimpleMeterRegistry();
        metrics = new WishInviteCallbackMetrics(registry, outboxMapper,
                Clock.fixed(FIXED, ZoneOffset.UTC));
    }

    @Test
    void refreshPublishesBacklogAndOldestWait() {
        when(outboxMapper.countPending()).thenReturn(12L);
        when(outboxMapper.countDuePending(NOW)).thenReturn(5L);
        when(outboxMapper.countFailed()).thenReturn(2L);
        when(outboxMapper.oldestPendingCreatedAt()).thenReturn(NOW.minusMinutes(30));

        metrics.refresh();

        assertThat(registry.get(WishInviteCallbackMetrics.PENDING).gauge().value()).isEqualTo(12);
        assertThat(registry.get(WishInviteCallbackMetrics.DUE).gauge().value()).isEqualTo(5);
        assertThat(registry.get(WishInviteCallbackMetrics.FAILED).gauge().value()).isEqualTo(2);
        assertThat(registry.get(WishInviteCallbackMetrics.OLDEST_PENDING_SECONDS).gauge().value())
                .isEqualTo(1800);
    }

    @Test
    void emptyQueueHasZeroOldestWait() {
        when(outboxMapper.oldestPendingCreatedAt()).thenReturn(null);

        metrics.refresh();

        assertThat(registry.get(WishInviteCallbackMetrics.OLDEST_PENDING_SECONDS)
                .gauge().value()).isZero();
    }

    @Test
    void refreshFailureKeepsLastSnapshot() {
        when(outboxMapper.countPending()).thenReturn(7L);
        metrics.refresh();
        when(outboxMapper.countPending()).thenThrow(new IllegalStateException("db down"));

        metrics.refresh();

        assertThat(registry.get(WishInviteCallbackMetrics.PENDING).gauge().value()).isEqualTo(7);
    }

    @Test
    void retryRatioCoversFailedAttempts() {
        metrics.recordSettled();
        metrics.recordSettled();
        metrics.recordRetry();
        metrics.recordTerminal();

        assertThat(registry.get(WishInviteCallbackMetrics.RETRY_RATIO).gauge().value())
                .isEqualTo(0.5);
        assertThat(registry.find(WishInviteCallbackMetrics.ATTEMPT).tag("outcome", "terminal")
                .counter().count()).isEqualTo(1);
    }
}
