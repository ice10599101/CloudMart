package com.cloudmart.wish.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 送礼限频组件（Redis 计数器，全站虚拟礼物）。
 *
 * <p>Key 规范：{@code wish:gift:rate:{userId}:{yyyyMMdd}}，TTL 至
 * Asia/Shanghai 当日 23:59:59（与 InteractionRateLimiter 同一约定）。
 * 每用户每日送礼 100 次——送礼消耗的是用户自己的星光，上限主要为防止
 * 直播间礼物广播刷屏与脚本误操作，而非资金风控。</p>
 *
 * <p><b>降级策略（Fail-Open）</b>：Redis 不可用时放行并记录 WARN 日志。
 * 送礼本身有星光余额约束（DB 条件更新），限频仅是防刷优化层。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GiftRateLimiter {

    /** 用户维度：送礼 100 次/日 */
    static final int LIMIT_USER_GIFT_DAILY = 100;

    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String KEY_PREFIX = "wish:gift:rate:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 用户维度送礼限频（当日按平台运营时区计算）。
     *
     * @return true=放行；false=已达上限（应由调用方返回 WISH_RATE_LIMITED）
     */
    public boolean checkSendDailyLimit(Long userId) {
        String key = KEY_PREFIX + userId + ":"
                + LocalDate.now(PLATFORM_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("送礼限频计数返回空，降级放行, key={}", key);
                return true;
            }
            if (count == 1L) {
                redisTemplate.expireAt(key, endOfDay());
            }
            return count <= LIMIT_USER_GIFT_DAILY;
        } catch (DataAccessException ex) {
            log.warn("Redis不可用，送礼限频降级放行（Fail-Open）, key={}", key, ex);
            return true;
        }
    }

    /**
     * 计算平台时区当日 23:59:59 的过期时间。
     * 若该时刻已异常早于当前时间（时钟回拨），退化为 24h 固定 TTL 防止永不过期。
     */
    private java.util.Date endOfDay() {
        LocalDateTime endOfDay = LocalDate.now(PLATFORM_ZONE).atTime(23, 59, 59);
        long epoch = endOfDay.atZone(PLATFORM_ZONE).toEpochSecond();
        long nowEpoch = Instant.now().getEpochSecond();
        if (epoch <= nowEpoch) {
            epoch = nowEpoch + TimeUnit.HOURS.toSeconds(24);
        }
        return new java.util.Date(epoch * 1000);
    }
}
