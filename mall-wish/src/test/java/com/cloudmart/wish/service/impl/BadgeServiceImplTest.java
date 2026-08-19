package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.entity.WishUserBadge;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.repository.WishBadgeMapper;
import com.cloudmart.wish.repository.WishUserBadgeMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.vo.BadgeDefinitionVO;
import com.cloudmart.wish.vo.BadgeWallItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BadgeServiceImpl 单元测试。
 *
 * <p>覆盖：达标授予/未达标跳过/已持有跳过/并发唯一键冲突幂等/无统计记录/
 * condition 非法跳过（Fail-Open）/徽章墙聚合排序与进度。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BadgeServiceImpl 单元测试")
class BadgeServiceImplTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private WishBadgeMapper wishBadgeMapper;
    @Mock
    private WishUserBadgeMapper wishUserBadgeMapper;
    @Mock
    private WishUserStatMapper wishUserStatMapper;

    private BadgeServiceImpl badgeService;

    @BeforeAll
    static void initEntityMeta() {
        // LambdaQueryWrapper 构造期解析 SFunction 列名，需要 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WishBadge.class);
        TableInfoHelper.initTableInfo(assistant, WishUserBadge.class);
        TableInfoHelper.initTableInfo(assistant, WishUserStat.class);
    }

    @BeforeEach
    void setUp() {
        badgeService = new BadgeServiceImpl(wishBadgeMapper, wishUserBadgeMapper, wishUserStatMapper);
    }

    private WishBadge badge(long id, String code, String condition) {
        WishBadge badge = new WishBadge();
        badge.setId(id);
        badge.setCode(code);
        badge.setName(code);
        badge.setIcon("");
        badge.setRarity("COMMON");
        badge.setCondition(condition);
        return badge;
    }

    private WishUserStat stat(Integer totalWishes, Integer totalHelped) {
        WishUserStat stat = new WishUserStat();
        stat.setUserId(USER_ID);
        stat.setTotalWishes(totalWishes);
        stat.setTotalHelped(totalHelped);
        stat.setTotalFulfilled(0);
        stat.setTotalCheckinDays(0);
        return stat;
    }

    @Nested
    @DisplayName("evaluateAndAward - 判定与授予")
    class EvaluateTests {

        @Test
        @DisplayName("达标：授予并返回新徽章列表")
        void award_onThresholdReached() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat(1, 0));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"发布第一个心愿\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());

            List<WishBadge> awarded = badgeService.evaluateAndAward(USER_ID);

            assertThat(awarded).hasSize(1);
            assertThat(awarded.get(0).getCode()).isEqualTo("FIRST_WISH");
            ArgumentCaptor<WishUserBadge> captor = ArgumentCaptor.forClass(WishUserBadge.class);
            verify(wishUserBadgeMapper).insert(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().getBadgeId()).isEqualTo(2001L);
        }

        @Test
        @DisplayName("未达标：不授予")
        void notAwarded_belowThreshold() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat(0, 4));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2003L, "HELP_100",
                            "{\"type\":\"TOTAL_HELPED\",\"threshold\":5,\"description\":\"帮助5人\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());

            List<WishBadge> awarded = badgeService.evaluateAndAward(USER_ID);

            assertThat(awarded).isEmpty();
            verify(wishUserBadgeMapper, never()).insert(any(WishUserBadge.class));
        }

        @Test
        @DisplayName("恰好等于阈值：授予（>= 语义）")
        void award_atExactThreshold() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat(0, 5));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2003L, "HELP_100",
                            "{\"type\":\"TOTAL_HELPED\",\"threshold\":5,\"description\":\"帮助5人\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());

            assertThat(badgeService.evaluateAndAward(USER_ID)).hasSize(1);
        }

        @Test
        @DisplayName("已持有：跳过不重复授予")
        void skip_alreadyEarned() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat(1, 0));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"x\"}")));
            WishUserBadge earned = new WishUserBadge();
            earned.setUserId(USER_ID);
            earned.setBadgeId(2001L);
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of(earned));

            assertThat(badgeService.evaluateAndAward(USER_ID)).isEmpty();
            verify(wishUserBadgeMapper, never()).insert(any(WishUserBadge.class));
        }

        @Test
        @DisplayName("并发唯一键冲突：忽略不报错（uk_user_badge 兜底幂等）")
        void award_duplicateKeyIgnored() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat(1, 0));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"x\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());
            when(wishUserBadgeMapper.insert(any(WishUserBadge.class)))
                    .thenThrow(new DuplicateKeyException("uk_user_badge"));

            assertThat(badgeService.evaluateAndAward(USER_ID)).isEmpty();
        }

        @Test
        @DisplayName("无统计记录：直接返回空")
        void noStat_emptyResult() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(null);

            assertThat(badgeService.evaluateAndAward(USER_ID)).isEmpty();
            verify(wishBadgeMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("condition 非法：跳过该徽章不阻断其他徽章判定（Fail-Open）")
        void invalidCondition_skipped() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat(1, 0));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2099L, "BROKEN", "{broken"),
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"x\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());

            List<WishBadge> awarded = badgeService.evaluateAndAward(USER_ID);

            assertThat(awarded).hasSize(1);
            assertThat(awarded.get(0).getCode()).isEqualTo("FIRST_WISH");
        }
    }

    @Nested
    @DisplayName("getBadgeWall / getDefinitions - 查询")
    class QueryTests {

        @Test
        @DisplayName("徽章墙：已获得在前（时间倒序），未获得含 condition+progress")
        void wall_orderingAndProgress() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat(3, 0));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"发布第一个心愿\"}"),
                    badge(2003L, "HELP_100",
                            "{\"type\":\"TOTAL_HELPED\",\"threshold\":100,\"description\":\"帮助100人\"}")));
            WishUserBadge earnedFirstWish = new WishUserBadge();
            earnedFirstWish.setUserId(USER_ID);
            earnedFirstWish.setBadgeId(2001L);
            earnedFirstWish.setCreatedAt(LocalDateTime.now().minusDays(1));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of(earnedFirstWish));

            List<BadgeWallItemVO> wall = badgeService.getBadgeWall(USER_ID);

            assertThat(wall).hasSize(2);
            BadgeWallItemVO earned = wall.get(0);
            assertThat(earned.getEarned()).isTrue();
            assertThat(earned.getEarnedAt()).isNotNull();
            assertThat(earned.getProgress().getCurrent()).isEqualTo(1);
            assertThat(earned.getProgress().getPercentage()).isEqualTo(100);
            BadgeWallItemVO locked = wall.get(1);
            assertThat(locked.getEarned()).isFalse();
            assertThat(locked.getEarnedAt()).isNull();
            assertThat(locked.getCondition()).isNotNull();
            assertThat(locked.getCondition().getType()).isEqualTo("TOTAL_HELPED");
            assertThat(locked.getProgress().getCurrent()).isEqualTo(0);
            assertThat(locked.getProgress().getPercentage()).isEqualTo(0);
        }

        @Test
        @DisplayName("徽章墙：无统计记录（新用户）进度 current=0 不抛异常")
        void wall_newUser() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(null);
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"x\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());

            List<BadgeWallItemVO> wall = badgeService.getBadgeWall(USER_ID);

            assertThat(wall).hasSize(1);
            assertThat(wall.get(0).getProgress().getCurrent()).isEqualTo(0);
        }

        @Test
        @DisplayName("图鉴：返回定义含 rarity 与 condition")
        void definitions() {
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2004L, "PERSIST_365",
                            "{\"type\":\"TOTAL_CHECKIN_DAYS\",\"threshold\":365,\"description\":\"累计打卡365天\"}")));

            List<BadgeDefinitionVO> definitions = badgeService.getDefinitions();

            assertThat(definitions).hasSize(1);
            BadgeDefinitionVO vo = definitions.get(0);
            assertThat(vo.getRarity()).isEqualTo("COMMON");
            assertThat(vo.getCondition().getThreshold()).isEqualTo(365);
            assertThat(vo.getDescription()).isEqualTo("累计打卡365天");
        }
    }

    @Nested
    @DisplayName("compensationScan - 漏发补偿扫描")
    class CompensationScanTests {

        private WishUserStat statRow(Long userId) {
            WishUserStat row = new WishUserStat();
            row.setUserId(userId);
            return row;
        }

        @Test
        @DisplayName("单批两用户：达标者补授、未达标者跳过，统计准确")
        void scanAwardsEligibleUsers() {
            when(wishUserStatMapper.selectList(any())).thenReturn(List.of(statRow(1001L), statRow(1002L)))
                    .thenReturn(List.of());
            // 1001 达标（无已持有），1002 无统计详情 → evaluateAndAward 内 selectById
            when(wishUserStatMapper.selectById(1001L)).thenReturn(stat(1, 0));
            when(wishUserStatMapper.selectById(1002L)).thenReturn(null);
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"x\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());

            BadgeService.CompensationResult result = badgeService.compensationScan();

            assertThat(result.scannedUsers()).isEqualTo(2);
            assertThat(result.awardedBadges()).isEqualTo(1);
            verify(wishUserBadgeMapper).insert(any(WishUserBadge.class));
        }

        @Test
        @DisplayName("空表：扫描 0 用户不报错")
        void scanEmptyTable() {
            when(wishUserStatMapper.selectList(any())).thenReturn(List.of());

            BadgeService.CompensationResult result = badgeService.compensationScan();

            assertThat(result.scannedUsers()).isZero();
            assertThat(result.awardedBadges()).isZero();
        }

        @Test
        @DisplayName("单用户异常：容错不中断，其余用户继续判定")
        void scanContinuesOnSingleUserFailure() {
            when(wishUserStatMapper.selectList(any())).thenReturn(List.of(statRow(1001L), statRow(1002L)))
                    .thenReturn(List.of());
            when(wishUserStatMapper.selectById(1001L)).thenThrow(new RuntimeException("db glitch"));
            when(wishUserStatMapper.selectById(1002L)).thenReturn(stat(1, 0));
            when(wishBadgeMapper.selectList(any())).thenReturn(List.of(
                    badge(2001L, "FIRST_WISH",
                            "{\"type\":\"WISH_CREATED\",\"threshold\":1,\"description\":\"x\"}")));
            when(wishUserBadgeMapper.selectList(any())).thenReturn(List.of());

            BadgeService.CompensationResult result = badgeService.compensationScan();

            assertThat(result.scannedUsers()).isEqualTo(2);
            assertThat(result.awardedBadges()).isEqualTo(1);
        }
    }
}
