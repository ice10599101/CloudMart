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
    @DisplayName("countContinuousDays（T09 逐日回溯：跨月自动切键）")
    class CountContinuousDaysTests {

        private void stubGetBit(String key, Long offset, boolean value) {
            when(valueOperations.getBit(eq(key), eq(offset))).thenReturn(value);
        }

        @Test
        @DisplayName("从锚点逐日回溯：9/10/11 日连续，8 日未签 → 3 天")
        void countContinuousDays_normal() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            stubGetBit("checkin:bitmap:1:202607", 10L, true);  // 11日
            stubGetBit("checkin:bitmap:1:202607", 9L, true);   // 10日
            stubGetBit("checkin:bitmap:1:202607", 8L, true);   // 9日
            stubGetBit("checkin:bitmap:1:202607", 7L, false);  // 8日

            int result = checkInBitMapService.countContinuousDays(USER_ID, LocalDate.of(2026, 7, 11));

            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("锚点当天未签到 → 0（连续链不预支未来）")
        void countContinuousDays_todayNotCheckedIn() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            stubGetBit("checkin:bitmap:1:202607", 10L, false); // 11日未签

            int result = checkInBitMapService.countContinuousDays(USER_ID, LocalDate.of(2026, 7, 11));

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("T09 回归：跨月不再限于本月第 1 天——7月29-31日 + 8月1日 → 4 天")
        void countContinuousDays_crossMonth() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            // 8月1日已签（旧实现只在本月第 1 天跨月，8月2日查连续会漏 7月31日）
            stubGetBit("checkin:bitmap:1:202608", 1L, true);   // 8月2日
            stubGetBit("checkin:bitmap:1:202608", 0L, true);   // 8月1日
            stubGetBit("checkin:bitmap:1:202607", 30L, true);  // 7月31日
            stubGetBit("checkin:bitmap:1:202607", 29L, true);  // 7月30日
            stubGetBit("checkin:bitmap:1:202607", 28L, true);  // 7月29日
            stubGetBit("checkin:bitmap:1:202607", 27L, false); // 7月28日

            int result = checkInBitMapService.countContinuousDays(USER_ID, LocalDate.of(2026, 8, 2));

            assertThat(result).isEqualTo(5); // 7月29-31日 + 8月1-2日（旧实现返回 4）
        }

        @Test
        @DisplayName("跨年边界：12月31日 → 1月1日 切年键")
        void countContinuousDays_crossYear() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            stubGetBit("checkin:bitmap:1:202701", 0L, true);    // 2027-01-01
            stubGetBit("checkin:bitmap:1:202612", 30L, true);   // 2026-12-31
            stubGetBit("checkin:bitmap:1:202612", 29L, false);  // 2026-12-30

            int result = checkInBitMapService.countContinuousDays(USER_ID, LocalDate.of(2027, 1, 1));

            assertThat(result).isEqualTo(2);
        }
    }

}
