package com.cloudmart.wish.it;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.service.DailySigninService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.vo.DailySigninVO;
import com.cloudmart.wish.vo.SigninCalendarVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 每日签到集成测试（文档 2.6：POST /wish/my/checkin + GET /wish/my/checkin/calendar）。
 *
 * <p>覆盖：首次签到（+5 星光流水/余额/连续 1 天）、重复签到 409、
 * 签到触发等级提升事件（文档 6.5 判定 + 只升不降落库）、
 * 日历聚合（当月日期/断签归零/总数）、month 格式校验、
 * 心愿打卡联动 total_checkin_days（等级 L2 判定依据）。</p>
 */
@DisplayName("每日签到集成测试")
class DailySigninIntegrationTest extends WishIntegrationTestBase {

    @Autowired
    private DailySigninService dailySigninService;

    @Autowired
    private WishService wishService;

    /** 服务端签到日期按用户时区去重（stat 缺省 Asia/Shanghai） */
    private static final ZoneId USER_TZ = ZoneId.of("Asia/Shanghai");

    private LocalDate today() {
        return LocalDate.now(USER_TZ);
    }

    private void seedSignin(long userId, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO wish_daily_signin (id, user_id, signin_date, starlight_granted, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, NOW(), NOW())",
                System.nanoTime(), userId, date);
    }

    private void seedStat(long userId, int balance, int totalWishes, int totalCheckinDays, int highestLevel) {
        jdbcTemplate.update(
                "INSERT INTO wish_user_stat (user_id, starlight_balance, total_wishes, total_checkin_days, "
                        + "level, level_title, highest_level, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 1, '追梦新人', ?, NOW(), NOW())",
                userId, balance, totalWishes, totalCheckinDays, highestLevel);
    }

    private int balanceOf(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT starlight_balance FROM wish_user_stat WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    @DisplayName("首次签到：+5 星光（SIGNIN 流水 + 余额）+ 连续 1 天 + levelUp 为 null")
    void signin_firstTime() {
        long userId = 4101L;
        seedUserStat(userId, 0);

        DailySigninVO vo = dailySigninService.signin(userId);

        assertThat(vo.signed()).isTrue();
        assertThat(vo.consecutiveDays()).isEqualTo(1);
        assertThat(vo.starlightReward()).isEqualTo(5);
        assertThat(vo.tomorrowReward()).isEqualTo(5);
        assertThat(vo.levelUp()).isNull();

        assertThat(balanceOf(userId)).isEqualTo(5);
        Integer delta = jdbcTemplate.queryForObject(
                "SELECT delta FROM wish_resource_log WHERE user_id = ? AND source = 'SIGNIN'",
                Integer.class, userId);
        assertThat(delta).isEqualTo(5);
        Integer signinRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wish_daily_signin WHERE user_id = ?",
                Integer.class, userId);
        assertThat(signinRows).isEqualTo(1);
    }

    @Test
    @DisplayName("重复签到：409 WISH_ALREADY_SIGNED_IN，星光不重复发放")
    void signin_repeated_409() {
        long userId = 4102L;
        seedUserStat(userId, 0);
        dailySigninService.signin(userId);

        assertThatThrownBy(() -> dailySigninService.signin(userId))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_ALREADY_SIGNED_IN));

        assertThat(balanceOf(userId)).isEqualTo(5);
    }

    @Test
    @DisplayName("签到触发等级提升：L1→L2 梦想家，事件返回且 stat 落库（只升不降）")
    void signin_levelUpEvent() {
        long userId = 4103L;
        // 指标已满足 L2（许愿 ≥ 3 且打卡 ≥ 7）但 highest_level 仍为 1：签到瞬间补判定
        seedStat(userId, 0, 3, 7, 1);

        DailySigninVO vo = dailySigninService.signin(userId);

        assertThat(vo.levelUp()).isNotNull();
        assertThat(vo.levelUp().previousLevel()).isEqualTo(1);
        assertThat(vo.levelUp().newLevel()).isEqualTo(2);
        assertThat(vo.levelUp().newLevelTitle()).isEqualTo("梦想家");

        Integer level = jdbcTemplate.queryForObject(
                "SELECT level FROM wish_user_stat WHERE user_id = ?", Integer.class, userId);
        Integer highest = jdbcTemplate.queryForObject(
                "SELECT highest_level FROM wish_user_stat WHERE user_id = ?", Integer.class, userId);
        String title = jdbcTemplate.queryForObject(
                "SELECT level_title FROM wish_user_stat WHERE user_id = ?", String.class, userId);
        assertThat(level).isEqualTo(2);
        assertThat(highest).isEqualTo(2);
        assertThat(title).isEqualTo("梦想家");

        // 再次签到（次日语义不可模拟，改为重复调用）：409 且等级保持
        assertThatThrownBy(() -> dailySigninService.signin(userId))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT highest_level FROM wish_user_stat WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("连续签到：昨日+今日 → 连续 2 天；日历返回当月日期与总数")
    void calendar_consecutiveAndAggregation() {
        long userId = 4104L;
        seedUserStat(userId, 0);
        LocalDate today = today();
        LocalDate yesterday = today.minusDays(1);
        LocalDate stale = today.minusDays(5);
        seedSignin(userId, yesterday);
        seedSignin(userId, today);
        seedSignin(userId, stale);

        // 先签到补今日？今日已直插 → 直接查日历（今日已在日历中）
        String month = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        SigninCalendarVO vo = dailySigninService.getCalendar(userId, month);

        // 当月日期：today/yesterday/stale 中落在当月的（跨月时自动收敛）
        long expectedInMonth = List.of(today, yesterday, stale).stream()
                .filter(d -> YearMonth.from(d).equals(YearMonth.from(today)))
                .count();
        assertThat(vo.signedDates()).hasSize((int) expectedInMonth);
        assertThat(vo.signedDates()).contains(today.toString());
        // 升序
        assertThat(vo.signedDates()).isSorted();
        assertThat(vo.totalDays()).isEqualTo(3);
        // 今日 + 昨日连续 → 2（stale 与 yesterday 间隔 4 天不连续）
        assertThat(vo.consecutiveDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("断签（最近签到早于昨日）：连续天数归 0")
    void calendar_brokenStreak() {
        long userId = 4105L;
        seedUserStat(userId, 0);
        seedSignin(userId, today().minusDays(3));

        String month = today().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        SigninCalendarVO vo = dailySigninService.getCalendar(userId, month);

        assertThat(vo.consecutiveDays()).isZero();
        assertThat(vo.totalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("month 格式非法（2026-9 / null / 空串）：400 WISH_VALIDATION_ERROR")
    void calendar_invalidMonth_400() {
        seedUserStat(4106L, 0);

        for (String badMonth : new String[] { null, "", "2026-9", "2026/09", "abc" }) {
            assertThatThrownBy(() -> dailySigninService.getCalendar(4106L, badMonth))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }
    }

    @Test
    @DisplayName("心愿打卡联动：total_checkin_days +1（文档 6.5，等级 L2 判定依据）")
    void wishCheckin_incrementsTotalCheckinDays() {
        long userId = 4107L;
        Long categoryId = seedCategory("IT_CHECKIN_LEVEL");
        stubUserFeign();
        seedUserStat(userId, 0);
        WishCreateResultVO created = wishService.createWish(
                userId, new CreateWishRequest("打卡等级联动", "验证打卡累计天数", null, categoryId,
                        List.of("测试"), WishVisibility.PUBLIC, null, null, null, null, null));

        wishService.checkinWish(userId, created.id(), "今日打卡");

        Integer totalCheckinDays = jdbcTemplate.queryForObject(
                "SELECT total_checkin_days FROM wish_user_stat WHERE user_id = ?",
                Integer.class, userId);
        assertThat(totalCheckinDays).isEqualTo(1);
    }
}
