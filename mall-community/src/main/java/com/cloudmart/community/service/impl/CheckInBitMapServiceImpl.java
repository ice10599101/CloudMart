package com.cloudmart.community.service.impl;

import com.cloudmart.community.service.CheckInBitMapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 Redis BitMap 的签到服务实现。
 * <p>
 * Key 设计：{@code checkin:bitmap:{userId}:{yyyyMM}}
 * <ul>
 *   <li>签到：SETBIT key (dayOfMonth-1) 1，返回旧值判断是否重复签到</li>
 *   <li>查询：GETBIT key (dayOfMonth-1)</li>
 *   <li>整月记录：BITFIELD key GET u{dayCount} 0，返回无符号整数后逐位解析</li>
 *   <li>连续天数：从 BITFIELD 结果的最高有效位（当天）开始向低位遍历，遇 0 停止</li>
 * </ul>
 * TTL 设置为 35 天 + 随机抖动（0~3600s），防止缓存雪崩。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInBitMapServiceImpl implements CheckInBitMapService {

    private static final String KEY_PREFIX = "checkin:bitmap:";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int BASE_TTL_DAYS = 35;
    private static final int JITTER_SECONDS = 3600;

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean setBit(Long userId, LocalDate date) {
        String key = buildKey(userId, date);
        long offset = (long) date.getDayOfMonth() - 1;
        Boolean previous = redisTemplate.opsForValue().setBit(key, offset, true);
        boolean alreadySet = Boolean.TRUE.equals(previous);
        if (!alreadySet) {
            refreshTtl(key);
        }
        return alreadySet;
    }

    @Override
    public boolean getBit(Long userId, LocalDate date) {
        String key = buildKey(userId, date);
        Boolean bit = redisTemplate.opsForValue().getBit(key, (long) date.getDayOfMonth() - 1);
        return Boolean.TRUE.equals(bit);
    }

    @Override
    public List<Integer> getMonthBits(Long userId, int year, int month, int dayCount) {
        if (dayCount <= 0) {
            return List.of();
        }
        String key = buildKey(userId, year, month);
        List<Long> result = redisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayCount))
                        .valueAt(0)
        );

        if (result == null || result.isEmpty() || result.get(0) == null) {
            List<Integer> zeros = new ArrayList<>(dayCount);
            for (int i = 0; i < dayCount; i++) {
                zeros.add(0);
            }
            return zeros;
        }

        long bits = result.get(0);
        List<Integer> bitList = new ArrayList<>(dayCount);
        // BITFIELD GET u{dayCount} 0：bit 0 (LSB) = offset 0 = 第1天
        for (int i = 0; i < dayCount; i++) {
            bitList.add((int) ((bits >> i) & 1));
        }
        return bitList;
    }

    /**
     * T09 修复：连续天数按"逐日回溯"计算——锚点当天必须已签到，否则返回 0。
     * 跨月/跨年自动切换月键（旧实现仅在本月第 1 天跨月，2月2日会漏掉 1月31日）。
     * 逐日 GETBIT 次数 = 连续链长度（位图 35 天 TTL 天然封顶），可接受。
     */
    @Override
    public int countContinuousDays(Long userId, LocalDate date) {
        int count = 0;
        LocalDate cursor = date;
        // 上限 40 天：超过位图 TTL 的历史位已不可信，连续链由 DB 事实负责（GrowthServiceImpl）
        for (int i = 0; i < 40; i++) {
            if (!getBit(userId, cursor)) {
                break;
            }
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    private String buildKey(Long userId, LocalDate date) {
        return KEY_PREFIX + userId + ":" + date.format(MONTH_FMT);
    }

    private String buildKey(Long userId, int year, int month) {
        return KEY_PREFIX + userId + ":" + YearMonth.of(year, month).format(MONTH_FMT);
    }

    private void refreshTtl(String key) {
        long ttlSeconds = Duration.ofDays(BASE_TTL_DAYS).getSeconds()
                + ThreadLocalRandom.current().nextInt(JITTER_SECONDS);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }
}
