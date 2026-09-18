package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.service.UploadQuotaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 每日上传配额实现（Redis 计数器）。
 *
 * <p>Key 规范：{@code file:quota:{image|other}:{userId}:{yyyyMMdd}}，
 * "当日"按平台运营时区 Asia/Shanghai 计算，TTL 至当日 23:59:59（与 mall-wish
 * InteractionRateLimiter 同一约定）。</p>
 *
 * <p><b>降级策略（Fail-Open）</b>：Redis 不可用时放行并记录 WARN 日志。
 * 上传额度是防刷限制而非数据正确性保障，可用性优先于限流精度；
 * 管理员上传（后台商品图/音频登记等）由 Controller 层豁免，不进入本服务。</p>
 */
@Slf4j
@Service
public class UploadQuotaServiceImpl implements UploadQuotaService {

    /** 图片类型配额桶（与本地存储 pic 分类扩展名一致） */
    static final String QUOTA_TYPE_IMAGE = "image";
    /** 非图片类型配额桶（视频/音乐/文档/压缩包等所有其他类型合计） */
    static final String QUOTA_TYPE_OTHER = "other";

    /** 平台运营时区：每日配额按 Asia/Shanghai 的自然日计算 */
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "file:quota:";

    /**
     * 退还额度的原子脚本：DECR 后若为负则回补至 0（用 INCRBY 回补而非 SET，
     * 避免覆盖/清除原有 TTL 导致 Key 永不过期）。
     */
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('DECR', KEYS[1]) "
                    + "if v < 0 then redis.call('INCRBY', KEYS[1], -v) end "
                    + "return v",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int imageDailyLimit;
    private final int otherDailyLimit;
    private final boolean enabled;
    private final Set<String> imageExtensions;

    public UploadQuotaServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${file.quota.image-daily-limit:30}") int imageDailyLimit,
            @Value("${file.quota.other-daily-limit:15}") int otherDailyLimit,
            @Value("${file.quota.enabled:true}") boolean enabled,
            @Value("${file.quota.image-extensions:jpg,jpeg,png,gif,bmp,webp,svg}") String imageExtensions) {
        this.redisTemplate = redisTemplate;
        this.imageDailyLimit = imageDailyLimit;
        this.otherDailyLimit = otherDailyLimit;
        this.enabled = enabled;
        this.imageExtensions = Set.of(imageExtensions.split(","));
    }

    @Override
    public boolean reserve(String userId, String filename) {
        if (!enabled) {
            return false;
        }
        String quotaType = quotaType(filename);
        String key = buildKey(quotaType, userId);
        int limit = limitOf(quotaType);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("上传配额计数返回空，降级放行（Fail-Open）, key={}", key);
                return false;
            }
            if (count == 1L) {
                redisTemplate.expireAt(key, endOfDay());
            }
            if (count > limit) {
                log.info("上传配额已达上限: userId={}, type={}, count={}, limit={}", userId, quotaType, count, limit);
                throw new BusinessException("UPLOAD_DAILY_LIMIT_EXCEEDED",
                        (QUOTA_TYPE_IMAGE.equals(quotaType) ? "今日图片上传已达上限（" : "今日文件上传已达上限（")
                                + limit + " 个），请明天再试");
            }
            return true;
        } catch (DataAccessException ex) {
            log.warn("Redis不可用，上传配额降级放行（Fail-Open）, key={}", key, ex);
            return false;
        }
    }

    @Override
    public void refund(String userId, String filename) {
        String key = buildKey(quotaType(filename), userId);
        try {
            redisTemplate.execute(REFUND_SCRIPT, List.of(key));
        } catch (DataAccessException ex) {
            // 预占与退还同用 Redis：预占失败时 reserve 已 Fail-Open 且不会调用本方法，
            // 此处异常说明预占成功后 Redis 恰好故障，多占一个名额可接受
            log.warn("Redis不可用，上传额度退还失败, key={}", key, ex);
        }
    }

    /** 按扩展名划分配额桶：图片类归 image，其余（视频/音乐/文档/压缩包等）全部归 other */
    private String quotaType(String filename) {
        String extension = extractExtension(filename).toLowerCase();
        return imageExtensions.contains(extension) ? QUOTA_TYPE_IMAGE : QUOTA_TYPE_OTHER;
    }

    private int limitOf(String quotaType) {
        return QUOTA_TYPE_IMAGE.equals(quotaType) ? imageDailyLimit : otherDailyLimit;
    }

    private String buildKey(String quotaType, String userId) {
        return KEY_PREFIX + quotaType + ":" + userId + ":"
                + LocalDate.now(PLATFORM_ZONE).format(KEY_DATE_FORMAT);
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

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }
}
