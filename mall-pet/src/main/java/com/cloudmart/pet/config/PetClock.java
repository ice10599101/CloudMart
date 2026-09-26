package com.cloudmart.pet.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 统一业务时钟（B04/B07）。
 *
 * <p>时间点口径：数据库存储与服务计算统一 UTC（{@link #nowUtc()}）；每日业务归属统一按
 * {@code pet.businessZone}（默认 Asia/Shanghai，北京时间 00:00 重置，§7.3 业务日切换基线）。
 * 接口输出层统一 RFC 3339 UTC（JacksonConfig 负责），本类不产出无时区字符串。</p>
 */
@Component
public class PetClock {

    private final Clock clock;
    private final PetProperties properties;

    public PetClock(Clock clock, PetProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** 当前 UTC 时间点（数据库 DATETIME 口径一致） */
    public LocalDateTime nowUtc() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    /** 当前 UTC 瞬间（跨时区换算用） */
    public Instant nowInstant() {
        return clock.instant();
    }

    /** 业务时区（每日重置边界） */
    public ZoneId businessZone() {
        return ZoneId.of(properties.getBusinessZone());
    }

    /** 当前业务日（businessZone 下的日期，每日任务/配额/陪伴归属） */
    public LocalDate businessDate() {
        return LocalDate.now(clock.withZone(businessZone()));
    }

    /** 指定 UTC 时间点对应的业务日（事件按发生时间归属，不按消费时间） */
    public LocalDate businessDateOf(LocalDateTime utcTime) {
        return utcTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(businessZone()).toLocalDate();
    }

    /** 下一个业务日重置点的 UTC 时间（nextResetAt 展示字段） */
    public LocalDateTime nextBusinessResetUtc() {
        LocalDateTime nowUtc = nowUtc();
        LocalDate tomorrow = businessDateOf(nowUtc).plusDays(1);
        return tomorrow.atStartOfDay(businessZone()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** 业务日重置点的 UTC 时间（指定业务日的 00:00） */
    public LocalDateTime businessDateStartUtc(LocalDate businessDate) {
        return businessDate.atStartOfDay(businessZone()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
