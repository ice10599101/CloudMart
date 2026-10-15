package com.cloudmart.software.service;

import com.cloudmart.software.config.SoftwareProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 11.2 限频纯逻辑单测：计数放行/拒绝、阈值 0 关闭、Redis 故障降级放行（Fail-Open）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareRateLimiterTest {

    private static final Long USER_ID = 42L;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SoftwareProperties properties;
    private SoftwareRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new SoftwareProperties();
        properties.setCommentPerUserMinute(5);
        limiter = new SoftwareRateLimiter(redisTemplate, properties,
                Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.systemDefault()));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("窗口内计数未超限放行；首次计数设置 TTL")
    void allowsUnderLimitAndSetsTtl() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThat(limiter.allowComment(USER_ID)).isTrue();
        verify(redisTemplate).expire(anyString(), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("超过阈值拒绝")
    void rejectsOverLimit() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        assertThat(limiter.allowComment(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("阈值为 0 表示不限制，不访问 Redis")
    void zeroLimitDisablesDimension() {
        properties.setCommentPerUserMinute(0);

        assertThat(limiter.allowComment(USER_ID)).isTrue();
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("Redis 故障降级放行（正确性由幂等/唯一键兜底，不参与鉴权裁决）")
    void failOpenOnRedisError() {
        when(valueOperations.increment(anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));

        assertThat(limiter.allowComment(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("各维度阈值取自配置")
    void dimensionsUseConfiguredLimits() {
        properties.setReportPerUserDay(2);
        when(valueOperations.increment(anyString())).thenReturn(2L);
        assertThat(limiter.allowReport(USER_ID)).isTrue();

        when(valueOperations.increment(anyString())).thenReturn(3L);
        assertThat(limiter.allowReport(USER_ID)).isFalse();
    }
}
