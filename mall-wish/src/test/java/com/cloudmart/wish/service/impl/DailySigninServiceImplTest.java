package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishDailySignin;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.repository.WishDailySigninMapper;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.DailySigninVO;
import com.cloudmart.wish.vo.LevelUpVO;
import com.cloudmart.wish.vo.SigninCalendarVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DailySigninServiceImpl 单元测试。
 *
 * <p>覆盖：首次签到（+5 星光 + 连续 1 天）、重复签到 409（预查 + 唯一键并发兜底）、
 * 签到触发等级提升事件、日历月份格式校验、断签后连续天数归零。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DailySigninServiceImpl 单元测试")
class DailySigninServiceImplTest {

    @Mock
    private WishDailySigninMapper wishDailySigninMapper;
    @Mock
    private UserStatService userStatService;

    private DailySigninServiceImpl dailySigninService;

    private static final Long USER_ID = 4001L;
    private static final ZoneId USER_TZ = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void setUp() {
        dailySigninService = new DailySigninServiceImpl(wishDailySigninMapper, userStatService);
        when(userStatService.getUserTimezone(USER_ID)).thenReturn("Asia/Shanghai");
    }

    @Nested
    @DisplayName("signin - 每日签到")
    class SigninTests {

        @Test
        @DisplayName("首次签到：+5 星光（SIGNIN 流水，refId=签到记录）+ 连续 1 天 + 无升级")
        void signin_firstTime() {
            LocalDate today = LocalDate.now(USER_TZ);
            when(wishDailySigninMapper.selectCount(any())).thenReturn(0L);
            // 插入后回填雪花 ID（refId 关联）
            doAnswerAssignId(9101L);
            when(userStatService.earnStarlight(eq(USER_ID), eq(5), eq(ResourceLogSource.SIGNIN), eq(9101L)))
                    .thenReturn(5);
            when(userStatService.checkAndLevelUp(USER_ID)).thenReturn(null);
            when(wishDailySigninMapper.countConsecutiveDays(eq(USER_ID), eq(today))).thenReturn(1);

            DailySigninVO vo = dailySigninService.signin(USER_ID);

            assertThat(vo.signed()).isTrue();
            assertThat(vo.consecutiveDays()).isEqualTo(1);
            assertThat(vo.starlightReward()).isEqualTo(5);
            assertThat(vo.tomorrowReward()).isEqualTo(5);
            assertThat(vo.levelUp()).isNull();

            ArgumentCaptor<WishDailySignin> captor = ArgumentCaptor.forClass(WishDailySignin.class);
            verify(wishDailySigninMapper).insert(captor.capture());
            assertThat(captor.getValue().getSigninDate()).isEqualTo(today);
            assertThat(captor.getValue().getStarlightGranted()).isTrue();
        }

        @Test
        @DisplayName("重复签到（预查命中）：409 WISH_ALREADY_SIGNED_IN，不发放星光")
        void signin_alreadySigned_409() {
            when(wishDailySigninMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> dailySigninService.signin(USER_ID))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_ALREADY_SIGNED_IN));
            verify(wishDailySigninMapper, never()).insert(any(WishDailySignin.class));
            verify(userStatService, never()).earnStarlight(anyLong(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("并发双击（uk_signin_daily 唯一键冲突）：409 兜底")
        void signin_duplicateKeyRace_409() {
            when(wishDailySigninMapper.selectCount(any())).thenReturn(0L);
            when(wishDailySigninMapper.insert(any(WishDailySignin.class)))
                    .thenThrow(new DuplicateKeyException("uk_signin_daily"));

            assertThatThrownBy(() -> dailySigninService.signin(USER_ID))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_ALREADY_SIGNED_IN));
            verify(userStatService, never()).earnStarlight(anyLong(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("签到触发等级提升：levelUp 事件透传（三端庆祝弹窗依据）")
        void signin_levelUpEvent() {
            LocalDate today = LocalDate.now(USER_TZ);
            when(wishDailySigninMapper.selectCount(any())).thenReturn(0L);
            doAnswerAssignId(9102L);
            when(userStatService.earnStarlight(anyLong(), anyInt(), any(), any())).thenReturn(5);
            LevelUpVO levelUp = new LevelUpVO(1, 2, "梦想家");
            when(userStatService.checkAndLevelUp(USER_ID)).thenReturn(levelUp);
            when(wishDailySigninMapper.countConsecutiveDays(eq(USER_ID), eq(today))).thenReturn(3);

            DailySigninVO vo = dailySigninService.signin(USER_ID);

            assertThat(vo.consecutiveDays()).isEqualTo(3);
            assertThat(vo.levelUp()).isEqualTo(levelUp);
        }

        @Test
        @DisplayName("余额达 5000 上限：入账截断为 0，签到仍成功")
        void signin_balanceCapped() {
            when(wishDailySigninMapper.selectCount(any())).thenReturn(0L);
            doAnswerAssignId(9103L);
            when(userStatService.earnStarlight(anyLong(), anyInt(), any(), any())).thenReturn(0);
            when(userStatService.checkAndLevelUp(USER_ID)).thenReturn(null);
            when(wishDailySigninMapper.countConsecutiveDays(anyLong(), any(LocalDate.class))).thenReturn(1);

            DailySigninVO vo = dailySigninService.signin(USER_ID);

            assertThat(vo.starlightReward()).isZero();
            assertThat(vo.signed()).isTrue();
        }

        private void doAnswerAssignId(long id) {
            when(wishDailySigninMapper.insert(any(WishDailySignin.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, WishDailySignin.class).setId(id);
                return 1;
            });
        }
    }

    @Nested
    @DisplayName("getCalendar - 签到日历")
    class CalendarTests {

        @Test
        @DisplayName("正常：当月已签到日期升序 + 总天数 + 当前连续天数")
        void getCalendar_normal() {
            LocalDate latest = LocalDate.now(USER_TZ);
            when(wishDailySigninMapper.selectList(any())).thenReturn(List.of(
                    buildSignin(latest.minusDays(1)), buildSignin(latest)));
            when(wishDailySigninMapper.selectCount(any())).thenReturn(7L);
            when(wishDailySigninMapper.selectOne(any())).thenReturn(buildSignin(latest));
            when(wishDailySigninMapper.countConsecutiveDays(eq(USER_ID), eq(latest))).thenReturn(2);

            String month = latest.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            SigninCalendarVO vo = dailySigninService.getCalendar(USER_ID, month);

            assertThat(vo.signedDates()).containsExactly(
                    latest.minusDays(1).toString(), latest.toString());
            assertThat(vo.consecutiveDays()).isEqualTo(2);
            assertThat(vo.totalDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("断签（最近签到早于昨日）：连续天数归 0")
        void getCalendar_brokenStreak_zero() {
            LocalDate stale = LocalDate.now(USER_TZ).minusDays(3);
            when(wishDailySigninMapper.selectOne(any())).thenReturn(buildSignin(stale));
            when(wishDailySigninMapper.selectList(any())).thenReturn(List.of());
            when(wishDailySigninMapper.selectCount(any())).thenReturn(1L);

            SigninCalendarVO vo = dailySigninService.getCalendar(USER_ID, "2026-09");

            assertThat(vo.consecutiveDays()).isZero();
            verify(wishDailySigninMapper, never()).countConsecutiveDays(anyLong(), any(LocalDate.class));
        }

        @Test
        @DisplayName("无任何签到：空日历 + 0 天")
        void getCalendar_empty() {
            when(wishDailySigninMapper.selectOne(any())).thenReturn(null);
            when(wishDailySigninMapper.selectList(any())).thenReturn(List.of());
            when(wishDailySigninMapper.selectCount(any())).thenReturn(0L);

            SigninCalendarVO vo = dailySigninService.getCalendar(USER_ID, "2026-09");

            assertThat(vo.signedDates()).isEmpty();
            assertThat(vo.consecutiveDays()).isZero();
            assertThat(vo.totalDays()).isZero();
        }

        @Test
        @DisplayName("month 格式非法（2026-9 / null / 空）：400 WISH_VALIDATION_ERROR")
        void getCalendar_invalidMonth_400() {
            for (String badMonth : new String[] { null, "", "2026-9", "2026/09", "abc" }) {
                assertThatThrownBy(() -> dailySigninService.getCalendar(USER_ID, badMonth))
                        .isInstanceOfSatisfying(BusinessException.class, ex ->
                                assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
            }
        }

        private WishDailySignin buildSignin(LocalDate date) {
            WishDailySignin signin = new WishDailySignin();
            signin.setUserId(USER_ID);
            signin.setSigninDate(date);
            signin.setStarlightGranted(true);
            return signin;
        }
    }
}
