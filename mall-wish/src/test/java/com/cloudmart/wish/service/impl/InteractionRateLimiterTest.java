package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.enums.InteractionType;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InteractionRateLimiter 单元测试。
 *
 * <p>覆盖文档第 32 章限频矩阵的边界：49/50/51（用户点亮）、心愿维度 200/201、
 * 祝福每愿望 1/2、TIL 至用户时区当日 23:59:59（跨日重置机制）、Redis Fail-Open。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InteractionRateLimiter 单元测试")
class InteractionRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private InteractionRateLimiter rateLimiter;

    private static final Long USER_ID = 1001L;
    private static final Long WISH_ID = 2001L;
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new InteractionRateLimiter(redisTemplate);
    }

    @Nested
    @DisplayName("checkUserDailyLimit - 用户维度日限频")
    class UserDailyLimitTests {

        @Test
        @DisplayName("点亮 49 次：放行（边界内）")
        void light_49_allowed() {
            when(valueOperations.increment(anyString())).thenReturn(49L);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.LIGHT, USER_ZONE)).isTrue();
        }

        @Test
        @DisplayName("点亮 50 次：放行（等于上限）")
        void light_50_allowed() {
            when(valueOperations.increment(anyString())).thenReturn(50L);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.LIGHT, USER_ZONE)).isTrue();
        }

        @Test
        @DisplayName("点亮 51 次：拒绝（超上限 429）")
        void light_51_rejected() {
            when(valueOperations.increment(anyString())).thenReturn(51L);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.LIGHT, USER_ZONE)).isFalse();
        }

        @Test
        @DisplayName("同求第 10 次放行、第 11 次拒绝")
        void sameWish_boundary() {
            when(valueOperations.increment(anyString())).thenReturn(10L);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.SAME_WISH, USER_ZONE)).isTrue();

            when(valueOperations.increment(anyString())).thenReturn(11L);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.SAME_WISH, USER_ZONE)).isFalse();
        }

        @Test
        @DisplayName("祝福第 20 次放行、第 21 次拒绝")
        void bless_boundary() {
            when(valueOperations.increment(anyString())).thenReturn(20L);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.BLESS, USER_ZONE)).isTrue();

            when(valueOperations.increment(anyString())).thenReturn(21L);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.BLESS, USER_ZONE)).isFalse();
        }

        @Test
        @DisplayName("首次计数（count=1）设置 TTL 至用户时区当日 23:59:59（跨日重置机制）")
        void firstIncrement_setsExpireAtEndOfDay() {
            when(valueOperations.increment(anyString())).thenReturn(1L);

            rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.LIGHT, USER_ZONE);

            String expectedKey = InteractionRateLimiter.buildUserKey(USER_ID, InteractionType.LIGHT);
            long expectedEpoch = LocalDate.now(USER_ZONE).atTime(23, 59, 59).atZone(USER_ZONE).toEpochSecond();
            verify(redisTemplate).expireAt(eq(expectedKey), any(Date.class));
            // 捕获 Date 验证过期时刻为当日 23:59:59（允许 2 秒执行误差）
            var captor = org.mockito.ArgumentCaptor.forClass(Date.class);
            verify(redisTemplate).expireAt(eq(expectedKey), captor.capture());
            long actualEpoch = captor.getValue().toInstant().getEpochSecond();
            assertThat(Math.abs(actualEpoch - expectedEpoch)).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("非首次计数不重复设置 TTL")
        void nonFirstIncrement_doesNotSetExpire() {
            when(valueOperations.increment(anyString())).thenReturn(5L);

            rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.LIGHT, USER_ZONE);

            verify(redisTemplate, never()).expireAt(anyString(), any(Date.class));
        }
    }

    @Nested
    @DisplayName("checkWishLightLimit - 心愿维度被点亮上限")
    class WishLightLimitTests {

        @Test
        @DisplayName("被点亮 200 次放行、201 次拒绝")
        void wishLight_boundary() {
            when(valueOperations.increment(anyString())).thenReturn(200L);
            assertThat(rateLimiter.checkWishLightLimit(WISH_ID)).isTrue();

            when(valueOperations.increment(anyString())).thenReturn(201L);
            assertThat(rateLimiter.checkWishLightLimit(WISH_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("checkBlessPerWish - 用户对同一心愿祝福限频")
    class BlessPerWishTests {

        @Test
        @DisplayName("当日第 1 次祝福放行、第 2 次拒绝")
        void blessPerWish_boundary() {
            when(valueOperations.increment(anyString())).thenReturn(1L);
            assertThat(rateLimiter.checkBlessPerWish(USER_ID, WISH_ID, USER_ZONE)).isTrue();

            when(valueOperations.increment(anyString())).thenReturn(2L);
            assertThat(rateLimiter.checkBlessPerWish(USER_ID, WISH_ID, USER_ZONE)).isFalse();
        }
    }

    @Nested
    @DisplayName("同求唯一占位（SETNX）")
    class SameWishUniqueTests {

        @Test
        @DisplayName("占位成功返回 true；已占用返回 false")
        void tryAcquire() {
            when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);
            assertThat(rateLimiter.tryAcquireSameWishUnique(USER_ID, WISH_ID)).isTrue();

            when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(false);
            assertThat(rateLimiter.tryAcquireSameWishUnique(USER_ID, WISH_ID)).isFalse();
        }

        @Test
        @DisplayName("释放占位删除 Key")
        void release_deletesKey() {
            rateLimiter.releaseSameWishUnique(USER_ID, WISH_ID);
            String expectedKey = "wish:rate:user_wish:" + USER_ID + ":" + WISH_ID + ":same_wish";
            verify(redisTemplate).delete(expectedKey);
        }
    }

    @Nested
    @DisplayName("Redis 故障降级（Fail-Open，文档 32.4）")
    class FailOpenTests {

        @Test
        @DisplayName("Redis 连接失败时限频放行（DB 唯一约束兜底）")
        void redisFailure_failOpen() {
            when(valueOperations.increment(anyString()))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.LIGHT, USER_ZONE)).isTrue();
            assertThat(rateLimiter.checkWishLightLimit(WISH_ID)).isTrue();
            assertThat(rateLimiter.checkBlessPerWish(USER_ID, WISH_ID, USER_ZONE)).isTrue();
        }

        @Test
        @DisplayName("同求占位 Redis 失败时放行")
        void acquire_failOpen() {
            when(valueOperations.setIfAbsent(anyString(), anyString()))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));
            assertThat(rateLimiter.tryAcquireSameWishUnique(USER_ID, WISH_ID)).isTrue();
        }

        @Test
        @DisplayName("释放占位 Redis 失败不抛异常（仅记录日志）")
        void release_failOpen() {
            when(redisTemplate.delete(anyString()))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));
            assertThatCode(() -> rateLimiter.releaseSameWishUnique(USER_ID, WISH_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("increment 返回 null 时降级放行")
        void incrementNull_failOpen() {
            when(valueOperations.increment(anyString())).thenReturn(null);
            assertThat(rateLimiter.checkUserDailyLimit(USER_ID, InteractionType.LIGHT, USER_ZONE)).isTrue();
        }
    }
}
