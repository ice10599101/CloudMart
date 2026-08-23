package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishAiProperties;
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
 * AI 功能限频组件（Redis 计数器，文档 30.3 / 32.3）。
 *
 * <p>Key 规范：{@code wish:rate:user:{userId}:ai_tree_hole}，TTL 至用户时区当日 23:59:59。</p>
 *
 * <p><b>降级策略（Fail-Open）</b>：Redis 不可用时放行请求并记录 WARN——
 * AI 调用无 DB 唯一约束兜底，超限仅产生额外成本，不破坏数据一致性，
 * 依据文档 32.4"限频仅是防刷与成本控制优化层"原则。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiRateLimiter {

    private static final String KEY_PREFIX = "wish:rate:user:";

    private final StringRedisTemplate redisTemplate;
    private final WishAiProperties aiProperties;

    /**
     * 树洞 AI 调用限频检查（当日按用户时区计算，文档 30.3：10 次/日）。
     *
     * @return true=放行；false=已达上限（调用方返回 429 WISH_AI_RATE_LIMITED）
     */
    public boolean checkTreeHoleDailyLimit(Long userId, ZoneId userZone) {
        return checkDailyLimit(userId, "ai_tree_hole", aiProperties.getTreeHoleDailyLimit(), userZone);
    }

    /**
     * 目标拆解 AI 调用限频检查（Sprint 2.5，当日按用户时区计算）。
     */
    public boolean checkGoalBreakdownDailyLimit(Long userId, ZoneId userZone) {
        return checkDailyLimit(userId, "ai_goal_breakdown", aiProperties.getGoalBreakdownDailyLimit(), userZone);
    }

    /**
     * 通用每日限频：Key {@code wish:rate:user:{userId}:{type}}，
     * TTL 至用户时区当日 23:59:59；Redis 异常 Fail-Open 放行。
     */
    public boolean checkDailyLimit(Long userId, String type, int limit, ZoneId userZone) {
        String key = KEY_PREFIX + userId + ":" + type;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("AI限频计数返回空，降级放行, key={}", key);
                return true;
            }
            if (count == 1L) {
                long expireAtEpoch = endOfDayEpoch(userZone);
                redisTemplate.expireAt(key, new java.util.Date(expireAtEpoch * 1000));
            }
            return count <= limit;
        } catch (DataAccessException ex) {
            log.warn("Redis不可用，AI限频降级放行（Fail-Open）, key={}", key, ex);
            return true;
        }
    }

    /**
     * 计算指定时区当日 23:59:59 的 epoch 秒；异常场景退化为 24h 固定 TTL。
     */
    private long endOfDayEpoch(ZoneId zone) {
        LocalDateTime endOfDay = LocalDate.now(zone).atTime(23, 59, 59);
        long epoch = endOfDay.atZone(zone).toEpochSecond();
        long nowEpoch = Instant.now().getEpochSecond();
        if (epoch <= nowEpoch) {
            return nowEpoch + TimeUnit.HOURS.toSeconds(24);
        }
        return epoch;
    }
}
