package com.cloudmart.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.DateTimeException;

/**
 * R22 统一业务日期口径：经济规则（签到、经验日配额、每日次数/收益）的"业务日"
 * 一律按用户 IANA 时区归属，不按服务器/设备默认时区。
 *
 * <p>防跨时区双份原理：业务归属日用用户时区，但"当日是否已操作"的判重用
 * {@link #businessDayStartUtc(LocalDate, ZoneId)} 得到的 <b>当前业务日 0 点 UTC 时刻</b>
 * 与操作事实的 UTC 发生时刻比较——用户怎么改时区，只要在"当前时区今日 0 点"之后有
 * 成功事实，就不允许第二次；历史已结算事实（带各自日期/UTC 时刻）不被回溯改写。</p>
 */
public final class UserBusinessCalendar {

    /** 用户缺省/非法时区的统一兜底（与 mall-wish wish_user_stat.timezone 默认一致） */
    public static final String DEFAULT_ZONE_ID = "Asia/Shanghai";

    private UserBusinessCalendar() {
    }

    /** 安全解析 IANA 时区；null/空白/非法一律回退默认业务区（fail-open，不阻断业务） */
    public static ZoneId zone(String iana) {
        if (iana == null || iana.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE_ID);
        }
        try {
            return ZoneId.of(iana.trim());
        } catch (DateTimeException invalid) {
            return ZoneId.of(DEFAULT_ZONE_ID);
        }
    }

    /** 指定 UTC 瞬间在用户时区下归属的业务日 */
    public static LocalDate businessDate(Instant utcInstant, ZoneId zone) {
        return utcInstant.atZone(zone).toLocalDate();
    }

    /** 当前业务日（用户时区） */
    public static LocalDate today(ZoneId zone) {
        return businessDate(Instant.now(), zone);
    }

    /** 用户业务日 0 点的 UTC 瞬间（当日事实判定/配额窗口的权威起点） */
    public static Instant businessDayStartUtc(LocalDate businessDate, ZoneId zone) {
        return businessDate.atStartOfDay(zone).toInstant();
    }

    /** 用户业务日 0 点的 UTC LocalDateTime（与库内 UTC DATETIME 比较用） */
    public static LocalDateTime businessDayStartUtcDateTime(LocalDate businessDate, ZoneId zone) {
        return LocalDateTime.ofInstant(businessDayStartUtc(businessDate, zone), ZoneOffset.UTC);
    }
}
