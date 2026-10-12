package com.cloudmart.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R22 用户业务日归属与日界 UTC 换算——防跨时区双签/双配额的数学基础。
 */
@DisplayName("UserBusinessCalendar 用户业务日")
class UserBusinessCalendarTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneId HONOLULU = ZoneId.of("Pacific/Honolulu");

    @Test
    @DisplayName("非法/空时区 fail-open 到默认 Asia/Shanghai")
    void invalidZoneFallsBackToDefault() {
        assertThat(UserBusinessCalendar.zone(null).getId()).isEqualTo("Asia/Shanghai");
        assertThat(UserBusinessCalendar.zone("  ").getId()).isEqualTo("Asia/Shanghai");
        assertThat(UserBusinessCalendar.zone("Mars/Olympus").getId()).isEqualTo("Asia/Shanghai");
        assertThat(UserBusinessCalendar.zone("asia/shanghai").getId()).isEqualTo("Asia/Shanghai");
    }

    @Test
    @DisplayName("UTC 瞬间按用户时区归属业务日")
    void businessDateByZone() {
        // 2026-10-12 16:30 UTC = 上海 10-13 00:30 / 檀香山 10-12 06:30
        Instant instant = Instant.parse("2026-10-12T16:30:00Z");
        assertThat(UserBusinessCalendar.businessDate(instant, SHANGHAI))
                .isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(UserBusinessCalendar.businessDate(instant, HONOLULU))
                .isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    @DisplayName("业务日 0 点 UTC 换算：上海 D 日 0 点 = UTC D-1 日 16:00")
    void shanghaiDayStartUtc() {
        assertThat(UserBusinessCalendar.businessDayStartUtc(LocalDate.of(2026, 10, 13), SHANGHAI))
                .isEqualTo(Instant.parse("2026-10-12T16:00:00Z"));
    }

    @Test
    @DisplayName("R22 防双签关键场景：上海 D 日凌晨签到（UTC D-1 16:30）后改檀香山，"
            + "该事实仍 >= 檀香山当前业务日(D-1)0 点 UTC(D-1 10:00) → 判定已签，拒绝第二次")
    void crossZoneDoubleCheckIn_prevented() {
        // 用户在上海 10-13 00:30 签到
        Instant shanghaiCheckIn = Instant.parse("2026-10-12T16:30:00Z");
        // 改时区到檀香山后"当前业务日"是 10-12
        LocalDate honoluluToday = UserBusinessCalendar.businessDate(Instant.now(), HONOLULU);
        // 用签到同一瞬间模拟：该瞬间檀香山业务日为 10-12
        honoluluToday = UserBusinessCalendar.businessDate(shanghaiCheckIn, HONOLULU);
        Instant honoluluDayStart = UserBusinessCalendar.businessDayStartUtc(honoluluToday, HONOLULU);
        // 檀香山 10-12 日 0 点 = UTC 10-12 10:00；签到时刻 16:30 在其之后 → 今日已签
        assertThat(honoluluDayStart).isEqualTo(Instant.parse("2026-10-12T10:00:00Z"));
        assertThat(shanghaiCheckIn.isAfter(honoluluDayStart) || shanghaiCheckIn.equals(honoluluDayStart))
                .isTrue();
    }

    @Test
    @DisplayName("正常跨天：上海 D 日 23:00 签到后，次日 D+1 00:30 早于新业务日界，允许再签")
    void normalNextDayAllowed() {
        Instant dayOneCheckIn = Instant.parse("2026-10-13T15:00:00Z"); // 上海 10-13 23:00
        Instant nextDayStart = UserBusinessCalendar.businessDayStartUtc(
                LocalDate.of(2026, 10, 14), SHANGHAI); // UTC 10-13 16:00
        // 次日签到时刻 10-13 16:30（上海 10-14 00:30）严格晚于日界 16:00，昨日签到不在新窗口内
        Instant dayTwoCheckIn = Instant.parse("2026-10-13T16:30:00Z");
        assertThat(dayOneCheckIn.isBefore(nextDayStart)).isTrue();
        assertThat(dayTwoCheckIn.isAfter(nextDayStart)).isTrue();
    }
}
