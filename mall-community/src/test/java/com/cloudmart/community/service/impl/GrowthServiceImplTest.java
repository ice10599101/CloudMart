package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.DailyCheckIn;
import com.cloudmart.community.entity.ExpLog;
import com.cloudmart.community.entity.LevelConfig;
import com.cloudmart.community.entity.UserLevel;
import com.cloudmart.community.repository.DailyCheckInMapper;
import com.cloudmart.community.repository.ExpLogMapper;
import com.cloudmart.community.repository.LevelConfigMapper;
import com.cloudmart.community.repository.UserLevelMapper;
import com.cloudmart.community.service.CheckInBitMapService;
import com.cloudmart.community.service.RankingService;
import com.cloudmart.community.vo.CheckInResultVO;
import com.cloudmart.community.vo.ExpLogVO;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserLevelVO;
import com.cloudmart.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
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

@ExtendWith(MockitoExtension.class)
class GrowthServiceImplTest {

    @Mock
    private UserLevelMapper userLevelMapper;

    @Mock
    private LevelConfigMapper levelConfigMapper;

    @Mock
    private DailyCheckInMapper dailyCheckInMapper;

    @Mock
    private ExpLogMapper expLogMapper;

    @Mock
    private RankingService rankingService;

    @Mock
    private CheckInBitMapService checkInBitMapService;

    private GrowthServiceImpl growthService;

    private static final Long USER_ID = 1L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant userLevelAssistant = new MapperBuilderAssistant(configuration, "");
        userLevelAssistant.setCurrentNamespace("com.cloudmart.community.repository.UserLevelMapper");
        TableInfoHelper.initTableInfo(userLevelAssistant, UserLevel.class);
        MapperBuilderAssistant levelConfigAssistant = new MapperBuilderAssistant(configuration, "");
        levelConfigAssistant.setCurrentNamespace("com.cloudmart.community.repository.LevelConfigMapper");
        TableInfoHelper.initTableInfo(levelConfigAssistant, LevelConfig.class);
        MapperBuilderAssistant checkInAssistant = new MapperBuilderAssistant(configuration, "");
        checkInAssistant.setCurrentNamespace("com.cloudmart.community.repository.DailyCheckInMapper");
        TableInfoHelper.initTableInfo(checkInAssistant, DailyCheckIn.class);
        MapperBuilderAssistant expLogAssistant = new MapperBuilderAssistant(configuration, "");
        expLogAssistant.setCurrentNamespace("com.cloudmart.community.repository.ExpLogMapper");
        TableInfoHelper.initTableInfo(expLogAssistant, ExpLog.class);
    }

    @BeforeEach
    void setUp() {
        growthService = new GrowthServiceImpl(
                userLevelMapper, levelConfigMapper, dailyCheckInMapper, expLogMapper, rankingService,
                checkInBitMapService);
    }

    private UserLevel buildUserLevel() {
        UserLevel userLevel = new UserLevel();
        userLevel.setId(1L);
        userLevel.setUserId(USER_ID);
        userLevel.setLevel(2);
        userLevel.setExp(50);
        userLevel.setTotalExp(50L);
        return userLevel;
    }

    private LevelConfig buildLevelConfig(int level, int minExp, String title) {
        LevelConfig config = new LevelConfig();
        config.setId((long) level);
        config.setLevel(level);
        config.setMinExp(minExp);
        config.setTitle(title);
        config.setIcon("icon-" + level);
        config.setBenefits("benefits-" + level);
        config.setStatus(1);
        return config;
    }

    @Nested
    @DisplayName("checkIn")
    class CheckInTests {

        @Test
        @DisplayName("should check in successfully for first time today")
        void checkIn_firstTimeToday() {
            when(checkInBitMapService.setBit(eq(USER_ID), any(LocalDate.class))).thenReturn(false);
            when(checkInBitMapService.countContinuousDays(eq(USER_ID), any(LocalDate.class))).thenReturn(1);
            when(dailyCheckInMapper.insert(any(DailyCheckIn.class))).thenAnswer(invocation -> {
                DailyCheckIn checkIn = invocation.getArgument(0);
                checkIn.setId(1L);
                return 1;
            });

            UserLevel userLevel = buildUserLevel();
            when(userLevelMapper.selectOne(any())).thenReturn(userLevel);
            when(levelConfigMapper.selectOne(any())).thenReturn(buildLevelConfig(2, 30, "Rookie"));

            growthService.checkIn(USER_ID);

            verify(dailyCheckInMapper).insert(any(DailyCheckIn.class));
            verify(expLogMapper).insert(any(ExpLog.class));
            verify(userLevelMapper).updateById(userLevel);
            verify(rankingService).addExpToRanking(USER_ID, 10);
        }

        @Test
        @DisplayName("should throw when already checked in today")
        void checkIn_alreadyCheckedIn_throwsException() {
            when(checkInBitMapService.setBit(eq(USER_ID), any(LocalDate.class))).thenReturn(true);

            assertThatThrownBy(() -> growthService.checkIn(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("ALREADY_CHECKED_IN");
                    });

            verify(dailyCheckInMapper, never()).insert(any(DailyCheckIn.class));
        }

        @Test
        @DisplayName("should calculate continuous days bonus correctly")
        void checkIn_continuousDaysBonus() {
            when(checkInBitMapService.setBit(eq(USER_ID), any(LocalDate.class))).thenReturn(false);
            when(checkInBitMapService.countContinuousDays(eq(USER_ID), any(LocalDate.class))).thenReturn(6);
            when(dailyCheckInMapper.insert(any(DailyCheckIn.class))).thenAnswer(invocation -> {
                DailyCheckIn checkIn = invocation.getArgument(0);
                checkIn.setId(2L);
                assertThat(checkIn.getContinuousDays()).isEqualTo(6);
                assertThat(checkIn.getExpReward()).isEqualTo(10 + Math.min(5 * 5, 50));
                return 1;
            });

            UserLevel userLevel = buildUserLevel();
            when(userLevelMapper.selectOne(any())).thenReturn(userLevel);
            when(levelConfigMapper.selectOne(any())).thenReturn(buildLevelConfig(2, 30, "Rookie"));

            growthService.checkIn(USER_ID);
        }
    }

    @Nested
    @DisplayName("isCheckedInToday")
    class IsCheckedInTodayTests {

        @Test
        @DisplayName("should return true when user has checked in")
        void isCheckedInToday_true() {
            when(checkInBitMapService.getBit(eq(USER_ID), any(LocalDate.class))).thenReturn(true);

            boolean result = growthService.isCheckedInToday(USER_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when user has not checked in")
        void isCheckedInToday_false() {
            when(checkInBitMapService.getBit(eq(USER_ID), any(LocalDate.class))).thenReturn(false);

            boolean result = growthService.isCheckedInToday(USER_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("addExp")
    class AddExpTests {

        @Test
        @DisplayName("should add exp and update user level")
        void addExp_success() {
            UserLevel userLevel = buildUserLevel();
            when(userLevelMapper.selectOne(any())).thenReturn(userLevel);
            when(levelConfigMapper.selectList(any())).thenReturn(List.of(
                    buildLevelConfig(3, 100, "Advanced"),
                    buildLevelConfig(2, 30, "Rookie"),
                    buildLevelConfig(1, 0, "Novice")
            ));

            growthService.addExp(USER_ID, 60, "POST", 100L, "发布帖子");

            assertThat(userLevel.getExp()).isEqualTo(110);
            assertThat(userLevel.getTotalExp()).isEqualTo(110L);
            verify(userLevelMapper).updateById(userLevel);
            verify(expLogMapper).insert(any(ExpLog.class));
            verify(rankingService).addExpToRanking(USER_ID, 60);
        }

        @Test
        @DisplayName("should create user level when not exists")
        void addExp_newUser() {
            when(userLevelMapper.selectOne(any())).thenReturn(null);
            when(userLevelMapper.insert(any(UserLevel.class))).thenAnswer(invocation -> {
                UserLevel ul = invocation.getArgument(0);
                ul.setId(1L);
                return 1;
            });
            when(levelConfigMapper.selectList(any())).thenReturn(List.of(
                    buildLevelConfig(1, 0, "Novice")
            ));

            growthService.addExp(USER_ID, 10, "COMMENT", 200L, "发表评论");

            verify(userLevelMapper).insert(any(UserLevel.class));
            verify(userLevelMapper).updateById(any(UserLevel.class));
            verify(expLogMapper).insert(any(ExpLog.class));
            verify(rankingService).addExpToRanking(USER_ID, 10);
        }
    }

    @Nested
    @DisplayName("getUserLevel")
    class GetUserLevelTests {

        @Test
        @DisplayName("should return UserLevelVO with progress")
        void getUserLevel_success() {
            UserLevel userLevel = buildUserLevel();
            when(userLevelMapper.selectOne(any())).thenReturn(userLevel);
            when(levelConfigMapper.selectOne(any())).thenAnswer(invocation -> {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LevelConfig> wrapper = invocation.getArgument(0);
                return buildLevelConfig(2, 30, "Rookie");
            });

            UserLevelVO result = growthService.getUserLevel(USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.level()).isEqualTo(2);
            assertThat(result.levelTitle()).isEqualTo("Rookie");
        }

        @Test
        @DisplayName("should create user level when not exists")
        void getUserLevel_newUser() {
            when(userLevelMapper.selectOne(any())).thenReturn(null);
            when(userLevelMapper.insert(any(UserLevel.class))).thenAnswer(invocation -> {
                UserLevel ul = invocation.getArgument(0);
                ul.setId(1L);
                return 1;
            });
            when(levelConfigMapper.selectOne(any())).thenReturn(buildLevelConfig(1, 0, "Novice"));

            UserLevelVO result = growthService.getUserLevel(USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.level()).isEqualTo(1);
            verify(userLevelMapper).insert(any(UserLevel.class));
        }
    }

    @Nested
    @DisplayName("getExpLogs")
    class GetExpLogsTests {

        @Test
        @DisplayName("should return paginated exp logs")
        void getExpLogs_success() {
            ExpLog expLog = new ExpLog();
            expLog.setId(1L);
            expLog.setUserId(USER_ID);
            expLog.setExpChange(10);
            expLog.setSource("CHECK_IN");
            expLog.setBizId(1L);
            expLog.setDescription("每日签到");

            Page<ExpLog> expLogPage = new Page<>(1, 10, 1);
            expLogPage.setRecords(List.of(expLog));
            when(expLogMapper.selectPage(any(Page.class), any())).thenReturn(expLogPage);

            Page<ExpLogVO> result = growthService.getExpLogs(USER_ID, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords().get(0).expChange()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("getLevelConfigs")
    class GetLevelConfigsTests {

        @Test
        @DisplayName("should return active level configs")
        void getLevelConfigs_success() {
            LevelConfig config = buildLevelConfig(1, 0, "Novice");
            when(levelConfigMapper.selectList(any())).thenReturn(List.of(config));

            List<LevelConfigVO> result = growthService.getLevelConfigs();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("Novice");
        }
    }

    @Nested
    @DisplayName("getCheckInCalendar")
    class GetCheckInCalendarTests {

        @Test
        @DisplayName("should return check-in dates for given month")
        void getCheckInCalendar_success() {
            // 假设2026年5月有31天，第1天和第15天签到了
            List<Integer> bits = new java.util.ArrayList<>(Collections.nCopies(31, 0));
            bits.set(0, 1);  // 第1天签到
            bits.set(14, 1); // 第15天签到

            when(checkInBitMapService.getMonthBits(eq(USER_ID), eq(2026), eq(5), anyInt()))
                    .thenReturn(bits);

            List<LocalDate> result = growthService.getCheckInCalendar(USER_ID, 2026, 5);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 15));
        }
    }

    @Nested
    @DisplayName("getContinuousDays")
    class GetContinuousDaysTests {

        @Test
        @DisplayName("should return continuous days when checked in today")
        void getContinuousDays_checkedInToday() {
            when(checkInBitMapService.getBit(eq(USER_ID), any(LocalDate.class))).thenReturn(true);
            when(checkInBitMapService.countContinuousDays(eq(USER_ID), any(LocalDate.class))).thenReturn(5);

            int result = growthService.getContinuousDays(USER_ID);

            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("should return 0 when not checked in today")
        void getContinuousDays_noCheckInToday() {
            when(checkInBitMapService.getBit(eq(USER_ID), any(LocalDate.class))).thenReturn(false);

            int result = growthService.getContinuousDays(USER_ID);

            assertThat(result).isEqualTo(0);
        }
    }
}
