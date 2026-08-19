package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiRateLimiter 单元测试（文档 30.3：树洞 10 次/日，边界 9/10/11）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiRateLimiter 单元测试")
class AiRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AiRateLimiter aiRateLimiter;

    private static final Long USER_ID = 1001L;
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        WishAiProperties properties = new WishAiProperties();
        properties.setTreeHoleDailyLimit(10);
        aiRateLimiter = new AiRateLimiter(redisTemplate, properties);
    }

    @Nested
    @DisplayName("checkTreeHoleDailyLimit - 树洞每日限频")
    class TreeHoleDailyLimitTests {

        @Test
        @DisplayName("第 9 次：放行（边界内）")
        void attempt9_allowed() {
            when(valueOperations.increment(anyString())).thenReturn(9L);
            assertThat(aiRateLimiter.checkTreeHoleDailyLimit(USER_ID, USER_ZONE)).isTrue();
        }

        @Test
        @DisplayName("第 10 次：放行（等于上限）")
        void attempt10_allowed() {
            when(valueOperations.increment(anyString())).thenReturn(10L);
            assertThat(aiRateLimiter.checkTreeHoleDailyLimit(USER_ID, USER_ZONE)).isTrue();
        }

        @Test
        @DisplayName("第 11 次：拒绝（超上限 429）")
        void attempt11_rejected() {
            when(valueOperations.increment(anyString())).thenReturn(11L);
            assertThat(aiRateLimiter.checkTreeHoleDailyLimit(USER_ID, USER_ZONE)).isFalse();
        }

        @Test
        @DisplayName("首次计数：设置 TTL 至用户时区当日 23:59:59")
        void firstIncrementShouldExpireAtEndOfDay() {
            when(valueOperations.increment(anyString())).thenReturn(1L);

            aiRateLimiter.checkTreeHoleDailyLimit(USER_ID, USER_ZONE);

            verify(redisTemplate).expireAt(anyString(), any(Date.class));
        }

        @Test
        @DisplayName("非首次计数：不重复设置 TTL")
        void subsequentIncrementShouldNotSetTtlAgain() {
            when(valueOperations.increment(anyString())).thenReturn(5L);

            aiRateLimiter.checkTreeHoleDailyLimit(USER_ID, USER_ZONE);

            verify(redisTemplate, never()).expireAt(anyString(), any(Date.class));
        }

        @Test
        @DisplayName("计数返回 null：降级放行")
        void nullCountShouldAllow() {
            when(valueOperations.increment(anyString())).thenReturn(null);
            assertThat(aiRateLimiter.checkTreeHoleDailyLimit(USER_ID, USER_ZONE)).isTrue();
        }

        @Test
        @DisplayName("Redis 不可用：Fail-Open 放行（成本控制优化层，不阻断业务）")
        void redisFailureShouldFailOpen() {
            when(valueOperations.increment(anyString()))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));
            assertThat(aiRateLimiter.checkTreeHoleDailyLimit(USER_ID, USER_ZONE)).isTrue();
        }
    }
}
