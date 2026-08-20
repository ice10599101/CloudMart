package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.enums.InteractionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 互动限频组件（Redis 计数器，文档第 32 章限频矩阵）。
 *
 * <p>Key 规范：{@code wish:rate:{维度}:{标识}:{type}}，TTL 至用户时区当日 23:59:59。
 * 心愿维度"当日"按平台运营时区 Asia/Shanghai 计算（无单一用户归属）。</p>
 *
 * <p><b>降级策略（Fail-Open）</b>：Redis 不可用时放行请求并记录 WARN 日志。
 * 依据文档 32.4 节原则"Redis 仅用于减少重复请求，数据库唯一约束才是最终正确性保障"——
 * 同求唯一由 {@code uk_interaction_unique} 函数索引兜底，限频仅是防刷优化层。</p>
 *
 * <p>限频矩阵：</p>
 * <ul>
 *   <li>用户维度：LIGHT 50/日、SAME_WISH 10/日、BLESS 20/日（总量）、ANON_STAR 3/日（Sprint 2.6）</li>
 *   <li>心愿维度：被点亮 200/日</li>
 *   <li>用户-心愿维度：BLESS 每愿望 1/日；SAME_WISH 永久唯一（SETNX）；
 *       ANON_STAR 每愿望 1 次（uk_interaction_unique 兜底，无需占位）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InteractionRateLimiter {

    /** 用户维度：点亮他人 50 次/日 */
    static final int LIMIT_USER_LIGHT_DAILY = 50;
    /** 用户维度：同求 10 次/日 */
    static final int LIMIT_USER_SAME_WISH_DAILY = 10;
    /** 用户维度：祝福总量 20 次/日 */
    static final int LIMIT_USER_BLESS_DAILY = 20;
    /** 用户维度：匿名星光 3 次/日（文档 4.1，Sprint 2.6 启用） */
    static final int LIMIT_USER_ANON_STAR_DAILY = 3;
    /** 心愿维度：被点亮 200 次/日 */
    static final int LIMIT_WISH_LIGHT_DAILY = 200;
    /** 用户-心愿维度：每愿望祝福 1 次/日 */
    static final int LIMIT_BLESS_PER_WISH_DAILY = 1;

    /** 心愿维度限频使用的运营时区 */
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String KEY_PREFIX = "wish:rate:";
    private static final String KEY_USER_DIMENSION = "user:";
    private static final String KEY_WISH_DIMENSION = "wish:";
    private static final String KEY_USER_WISH_DIMENSION = "user_wish:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 用户维度限频检查（当日按用户时区计算）。
     *
     * @param userId      用户 ID
     * @param type        互动类型（LIGHT/SAME_WISH/BLESS/ANON_STAR）
     * @param userZone    用户时区
     * @return true=放行；false=已达上限（应由调用方返回 429）
     */
    public boolean checkUserDailyLimit(Long userId, InteractionType type, ZoneId userZone) {
        int limit = switch (type) {
            case LIGHT -> LIMIT_USER_LIGHT_DAILY;
            case SAME_WISH -> LIMIT_USER_SAME_WISH_DAILY;
            case BLESS -> LIMIT_USER_BLESS_DAILY;
            case ANON_STAR -> LIMIT_USER_ANON_STAR_DAILY;
        };
        String key = KEY_PREFIX + KEY_USER_DIMENSION + userId + ":" + type.name().toLowerCase();
        return incrementAndCheck(key, limit, userZone);
    }

    /**
     * 心愿维度被点亮上限（200 次/日，按平台运营时区）。
     *
     * @return true=放行；false=该心愿今日被点亮已达上限
     */
    public boolean checkWishLightLimit(Long wishId) {
        String key = KEY_PREFIX + KEY_WISH_DIMENSION + wishId + ":light";
        return incrementAndCheck(key, LIMIT_WISH_LIGHT_DAILY, PLATFORM_ZONE);
    }

    /**
     * 用户对同一心愿的祝福限频（1 次/日）。
     *
     * @return true=放行；false=该心愿今日已祝福过
     */
    public boolean checkBlessPerWish(Long userId, Long wishId, ZoneId userZone) {
        String key = KEY_PREFIX + KEY_USER_WISH_DIMENSION + userId + ":" + wishId + ":bless";
        return incrementAndCheck(key, LIMIT_BLESS_PER_WISH_DAILY, userZone);
    }

    /**
     * 同求唯一占位（SETNX 永久，文档 32.3）。
     *
     * <p>取消同求时调用 {@link #releaseSameWishUnique} 释放，允许重新同求——
     * 与数据库函数唯一索引语义一致（仅未删除记录参与唯一约束，软删后可重新插入）。</p>
     *
     * @return true=占位成功；false=已同求过
     */
    public boolean tryAcquireSameWishUnique(Long userId, Long wishId) {
        String key = KEY_PREFIX + KEY_USER_WISH_DIMENSION + userId + ":" + wishId + ":same_wish";
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(Instant.now().toEpochMilli()));
            return Boolean.TRUE.equals(acquired);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis不可用，同求唯一占位降级放行（DB唯一索引兜底）, key={}", key, ex);
            return true;
        }
    }

    /**
     * 释放同求唯一占位（取消同求时调用，允许重新同求）。
     */
    public void releaseSameWishUnique(Long userId, Long wishId) {
        String key = KEY_PREFIX + KEY_USER_WISH_DIMENSION + userId + ":" + wishId + ":same_wish";
        try {
            redisTemplate.delete(key);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis不可用，同求唯一占位释放失败（残留Key会导致需人工处理）, key={}", key, ex);
        }
    }

    /**
     * 原子自增并判断限额；首次自增时设置当日 23:59:59（指定时区）过期。
     * Redis 异常时 Fail-Open 放行。
     */
    private boolean incrementAndCheck(String key, int limit, ZoneId zone) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("限频计数返回空，降级放行, key={}", key);
                return true;
            }
            if (count == 1L) {
                long expireAtEpoch = endOfDayEpoch(zone);
                redisTemplate.expireAt(key, new java.util.Date(expireAtEpoch * 1000));
            }
            return count <= limit;
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis不可用，限频降级放行（Fail-Open）, key={}", key, ex);
            return true;
        }
    }

    /**
     * 计算指定时区当日 23:59:59 的 epoch 秒。
     * 若该时刻已异常晚于当前时间 1 天以上（时钟回拨等），退化为 24h 固定 TTL 防止永不过期。
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

    /**
     * 供测试与运维观测：构造用户维度限频 Key。
     */
    static String buildUserKey(Long userId, InteractionType type) {
        return KEY_PREFIX + KEY_USER_DIMENSION + userId + ":" + type.name().toLowerCase();
    }
}
