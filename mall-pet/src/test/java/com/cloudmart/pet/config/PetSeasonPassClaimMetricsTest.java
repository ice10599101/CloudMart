package com.cloudmart.pet.config;

import com.cloudmart.pet.repository.PetSeasonPassClaimMapper;
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

/** T18：补发积压/最老等待/重试率/终止数指标的采集口径。 */
class PetSeasonPassClaimMetricsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 10, 0);
    private static final Instant FIXED = NOW.toInstant(ZoneOffset.UTC);

    private PetSeasonPassClaimMapper claimMapper;
    private SimpleMeterRegistry registry;
    private PetSeasonPassClaimMetrics metrics;

    @BeforeEach
    void setUp() {
        claimMapper = mock(PetSeasonPassClaimMapper.class);
        registry = new SimpleMeterRegistry();
        PetClock clock = new PetClock(Clock.fixed(FIXED, ZoneOffset.UTC), new PetProperties());
        metrics = new PetSeasonPassClaimMetrics(registry, claimMapper, clock);
    }

    @Test
    void refreshPublishesBacklogAndOldestWait() {
        when(claimMapper.countPending()).thenReturn(12L);
        when(claimMapper.countDuePending(NOW)).thenReturn(5L);
        when(claimMapper.countNeedsAttention()).thenReturn(2L);
        when(claimMapper.oldestPendingCreatedAt()).thenReturn(NOW.minusMinutes(30));

        metrics.refresh();

        assertThat(registry.get(PetSeasonPassClaimMetrics.PENDING).gauge().value()).isEqualTo(12);
        assertThat(registry.get(PetSeasonPassClaimMetrics.DUE).gauge().value()).isEqualTo(5);
        assertThat(registry.get(PetSeasonPassClaimMetrics.NEEDS_ATTENTION).gauge().value()).isEqualTo(2);
        assertThat(registry.get(PetSeasonPassClaimMetrics.OLDEST_PENDING_SECONDS).gauge().value())
                .isEqualTo(1800);
    }

    @Test
    void emptyQueueHasZeroOldestWait() {
        when(claimMapper.oldestPendingCreatedAt()).thenReturn(null);

        metrics.refresh();

        assertThat(registry.get(PetSeasonPassClaimMetrics.OLDEST_PENDING_SECONDS).gauge().value()).isZero();
    }

    @Test
    void refreshFailureKeepsLastSnapshot() {
        when(claimMapper.countPending()).thenReturn(7L);
        metrics.refresh();
        when(claimMapper.countPending()).thenThrow(new IllegalStateException("db down"));

        metrics.refresh();

        assertThat(registry.get(PetSeasonPassClaimMetrics.PENDING).gauge().value()).isEqualTo(7);
    }

    @Test
    void retryRatioCoversFailedAttempts() {
        metrics.recordSettled();
        metrics.recordSettled();
        metrics.recordRetry();
        metrics.recordTerminal();

        assertThat(registry.get(PetSeasonPassClaimMetrics.RETRY_RATIO).gauge().value())
                .isEqualTo(0.5);
        assertThat(registry.find(PetSeasonPassClaimMetrics.ATTEMPT).tag("outcome", "terminal")
                .counter().count()).isEqualTo(1);
        metrics.recordManualRetry();
        assertThat(registry.get(PetSeasonPassClaimMetrics.MANUAL_RETRY).counter().count()).isEqualTo(1);
    }
}
