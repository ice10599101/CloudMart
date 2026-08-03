package com.cloudmart.community.service.impl;

import com.cloudmart.community.service.CheckInBitMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInBitMapServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CheckInBitMapService checkInBitMapService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        checkInBitMapService = new CheckInBitMapServiceImpl(redisTemplate);
    }

    // ======================== setBit ========================

    @Nested
    @DisplayName("setBit")
    class SetBitTests {

        @Test
        @DisplayName("should return false and refresh TTL on first check-in")
        void setBit_firstTime() {
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setBit(anyString(), anyLong(), eq(true))).thenReturn(false);

            boolean result = checkInBitMapService.setBit(USER_ID, date);

            assertThat(result).isFalse();
            verify(redisTemplate).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should return true and skip TTL refresh on duplicate check-in")
        void setBit_alreadySet() {
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setBit(anyString(), anyLong(), eq(true))).thenReturn(true);

            boolean result = checkInBitMapService.setBit(USER_ID, date);

            assertThat(result).isTrue();
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should treat null return as first time")
        void setBit_nullReturn() {
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setBit(anyString(), anyLong(), eq(true))).thenReturn(null);

            boolean result = checkInBitMapService.setBit(USER_ID, date);

            assertThat(result).isFalse();
            verify(redisTemplate).expire(anyString(), any(Duration.class));
        }
    }

    // ======================== getBit ========================

    @Nested
    @DisplayName("getBit")
    class GetBitTests {

        @Test
        @DisplayName("should return true when bit is set")
        void getBit_true() {
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getBit(anyString(), anyLong())).thenReturn(true);

            boolean result = checkInBitMapService.getBit(USER_ID, date);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when bit is not set")
        void getBit_false() {
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getBit(anyString(), anyLong())).thenReturn(false);

            boolean result = checkInBitMapService.getBit(USER_ID, date);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Redis returns null")
        void getBit_null() {
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getBit(anyString(), anyLong())).thenReturn(null);

            boolean result = checkInBitMapService.getBit(USER_ID, date);

            assertThat(result).isFalse();
        }
    }

    // ======================== getMonthBits ========================

    @Nested
    @DisplayName("getMonthBits")
    class GetMonthBitsTests {

        @Test
        @DisplayName("should parse bits correctly from BITFIELD result")
        void getMonthBits_normal() {
            // 假设第1天和第3天签到了：bit 0 = 1, bit 1 = 0, bit 2 = 1 → 整数 = 5
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of(5L));

            List<Integer> result = checkInBitMapService.getMonthBits(USER_ID, 2026, 7, 3);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(1); // 第1天
            assertThat(result.get(1)).isEqualTo(0); // 第2天
            assertThat(result.get(2)).isEqualTo(1); // 第3天
        }

        @Test
        @DisplayName("should return all zeros when BitMap does not exist")
        void getMonthBits_emptyBitMap() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of());

            List<Integer> result = checkInBitMapService.getMonthBits(USER_ID, 2026, 7, 5);

            assertThat(result).hasSize(5);
            assertThat(result).allMatch(bit -> bit == 0);
        }

        @Test
        @DisplayName("should return all zeros when BITFIELD returns null")
        void getMonthBits_nullResult() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(null);

            List<Integer> result = checkInBitMapService.getMonthBits(USER_ID, 2026, 7, 5);

            assertThat(result).hasSize(5);
            assertThat(result).allMatch(bit -> bit == 0);
        }

        @Test
        @DisplayName("should return empty list when dayCount is zero or negative")
        void getMonthBits_nonPositiveDayCount() {
            List<Integer> result = checkInBitMapService.getMonthBits(USER_ID, 2026, 7, 0);

            assertThat(result).isEmpty();
            verify(redisTemplate, never()).opsForValue();
        }
    }

    // ======================== countContinuousDays ========================

    @Nested
    @DisplayName("countContinuousDays")
    class CountContinuousDaysTests {

        @Test
        @DisplayName("should count continuous days from today backwards")
        void countContinuousDays_normal() {
            // 7月11日，连续签到3天（9、10、11日）
            // bit 8 = 1 (9日), bit 9 = 1 (10日), bit 10 = 1 (11日), bit 7 = 0 (8日)
            // 整数 = 2^8 + 2^9 + 2^10 = 256 + 512 + 1024 = 1792
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of(1792L));

            int result = checkInBitMapService.countContinuousDays(USER_ID, date);

            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("should stop at first 0 when counting backwards")
        void countContinuousDays_broken() {
            // 7月5日，签到了1、2、4、5日（3日没签）
            // bit 0 = 1, bit 1 = 1, bit 2 = 0, bit 3 = 1, bit 4 = 1
            // 整数 = 1 + 2 + 8 + 16 = 27
            LocalDate date = LocalDate.of(2026, 7, 5);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of(27L));

            int result = checkInBitMapService.countContinuousDays(USER_ID, date);

            assertThat(result).isEqualTo(2); // 5日和4日
        }

        @Test
        @DisplayName("should return 0 when BITFIELD returns null")
        void countContinuousDays_nullResult() {
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(null);

            int result = checkInBitMapService.countContinuousDays(USER_ID, date);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 when today is not checked in")
        void countContinuousDays_todayNotCheckedIn() {
            // 7月11日，只签到了1-10日，11日没签
            // bit 10 = 0 → 从最高位开始就是0 → 返回0
            // 整数 = 2^0 + 2^1 + ... + 2^9 = 1023
            LocalDate date = LocalDate.of(2026, 7, 11);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of(1023L));

            int result = checkInBitMapService.countContinuousDays(USER_ID, date);

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle cross-month when today is 1st and prev month last day signed")
        void countContinuousDays_crossMonth() {
            // 8月1日，已签到（bit 0 = 1 → 整数 = 1）
            // 需要查上月7月31日是否签到 → getBit 返回 true
            // 然后查7月的 BITFIELD，假设7月29、30、31日连续签到
            // 7月31日：bit 30 = 1 (31日), bit 29 = 1 (30日), bit 28 = 1 (29日), bit 27 = 0 (28日)
            // 整数 = 2^30 + 2^29 + 2^28 = 1073741824 + 536870912 + 268435456 = 1879048192
            LocalDate augFirst = LocalDate.of(2026, 8, 1);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            // 8月 BITFIELD 返回 1（8月1日签到了）
            when(valueOperations.bitField(eq("checkin:bitmap:1:202608"), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of(1L));
            // 7月31日 getBit 返回 true
            when(valueOperations.getBit(eq("checkin:bitmap:1:202607"), eq(30L))).thenReturn(true);
            // 7月 BITFIELD 返回 1879048192（29、30、31日签到了）
            when(valueOperations.bitField(eq("checkin:bitmap:1:202607"), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of(1879048192L));

            int result = checkInBitMapService.countContinuousDays(USER_ID, augFirst);

            // 8月1日（1天）+ 7月30、31日（2天，不含31日本身已在调用方计数）
            // 等等，countContinuousDaysPrevMonth 从倒数第2天开始统计
            // 7月31日已签到（调用方计1），countContinuousDaysPrevMonth 统计 7月30日、29日
            // 7月 BITFIELD 整数 = 2^30 + 2^29 + 2^28
            // 从 bit 29（30日）开始向前：bit 29 = 1 → count++, bit 28 = 1 → count++, bit 27 = 0 → 停止
            // 所以 countContinuousDaysPrevMonth 返回 2
            // 总计 = 1（8月1日） + 2（7月30、29日） = 3
            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("should not check prev month when today is 1st but not signed")
        void countContinuousDays_firstDayNotSigned() {
            // 8月1日没签到 → 整数 = 0 → count = 0 → 不触发跨月检查
            LocalDate augFirst = LocalDate.of(2026, 8, 1);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class)))
                    .thenReturn(List.of(0L));

            int result = checkInBitMapService.countContinuousDays(USER_ID, augFirst);

            assertThat(result).isEqualTo(0);
            // 不应该查上月的 getBit
            verify(valueOperations, never()).getBit(eq("checkin:bitmap:1:202607"), anyLong());
        }
    }
}
