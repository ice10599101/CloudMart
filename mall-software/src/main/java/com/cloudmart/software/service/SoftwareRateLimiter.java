package com.cloudmart.software.service;

import com.cloudmart.software.config.SoftwareProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 软件模块限频组件（方案 11.2 限流与配置）：Redis 计数器，key 前缀
 * {@code software:rate:} 与其它模块隔离。
 *
 * <p>矩阵：评论 5/分、获取链接 30/分、失效反馈 20/日、举报 20/日、提交审核 10/日，
 * 阈值全部来自 {@link SoftwareProperties}（部署压测后可调）。</p>
 *
 * <p><b>降级策略（Fail-Open）</b>：Redis 不可用时放行并 WARN。理由与既有互动限频一致：
 * 限流是防刷优化层，正确性由软件域的幂等事实（software_operation）、
 * 日获取去重唯一键与各状态机校验兜底；限流层故障不得打挂模块，也不参与任何
 * 鉴权/资格/授权裁决（限流故障不会绕过身份边界）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareRateLimiter {

    /** 平台运营时区（"当日"口径与手册一致） */
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String KEY_PREFIX = "software:rate:";

    private final StringRedisTemplate redisTemplate;
    private final SoftwareProperties softwareProperties;
    private final Clock clock;

    /** 评论：单用户每分钟上限 */
    public boolean allowComment(Long userId) {
        return allow("comment", userId, softwareProperties.getCommentPerUserMinute(),
                Duration.ofMinutes(1));
    }

    /** 获取链接：单用户每分钟上限 */
    public boolean allowResolve(Long userId) {
        return allow("resolve", userId, softwareProperties.getResolvePerUserMinute(),
                Duration.ofMinutes(1));
    }

    /** 失效反馈：单用户每日上限 */
    public boolean allowFeedback(Long userId) {
        return allow("feedback", userId, softwareProperties.getFeedbackPerUserDay(),
                Duration.ofDays(1));
    }

    /** 举报：单用户每日上限 */
    public boolean allowReport(Long userId) {
        return allow("report", userId, softwareProperties.getReportPerUserDay(),
                Duration.ofDays(1));
    }

    /** 提交审核：单用户每日上限 */
    public boolean allowSubmit(Long userId) {
        return allow("submit", userId, softwareProperties.getPublishSubmissionsPerUserDay(),
                Duration.ofDays(1));
    }

    /**
     * 计数并判定是否放行。
     *
     * @param dimension 维度名（进入 Redis key，禁止用户输入）
     * @param window    窗口粒度：分钟按 epoch 分钟分桶，日按平台时区分桶
     */
    private boolean allow(String dimension, Long userId, int limit, Duration window) {
        if (limit <= 0) {
            return true; // 0=不限制（允许运营通过配置关闭该维度限流）
        }
        try {
            long ttlSeconds = window.compareTo(Duration.ofHours(1)) < 0
                    ? window.toSeconds() * 2
                    : secondsUntilNextPlatformDay() + 120;
            String bucket = window.compareTo(Duration.ofHours(1)) < 0
                    ? String.valueOf(Instant.now(clock).getEpochSecond() / 60)
                    : LocalDate.now(clock.withZone(PLATFORM_ZONE)).toString();
            String key = KEY_PREFIX + dimension + ":" + userId + ":" + bucket;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            // 降级放行：Redis 故障不改变身份边界，不绕过任何授权裁决
            log.warn("软件限流降级放行 dimension={} userId={}: {}", dimension, userId,
                    e.getMessage());
            return true;
        }
    }

    private long secondsUntilNextPlatformDay() {
        return ChronoUnit.SECONDS.between(
                Instant.now(clock),
                LocalDate.now(clock.withZone(PLATFORM_ZONE)).plusDays(1)
                        .atStartOfDay(PLATFORM_ZONE).toInstant());
    }
}
