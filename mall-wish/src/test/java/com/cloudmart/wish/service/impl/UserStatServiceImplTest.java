package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishResourceLog;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.ResourceLogType;
import com.cloudmart.wish.repository.WishResourceLogMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserStatServiceImpl 星光账本单元测试。
 *
 * <p>覆盖：扣减不足 402、扣减与流水同事务快照、发放上限 5000 截断、
 * 上限后不再入账、时区默认值、统计幂等初始化。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserStatServiceImpl 单元测试")
class UserStatServiceImplTest {

    @Mock
    private WishUserStatMapper wishUserStatMapper;
    @Mock
    private WishResourceLogMapper wishResourceLogMapper;

    private UserStatServiceImpl userStatService;

    private static final Long USER_ID = 1001L;

    @BeforeAll
    static void initEntityMeta() {
        // LambdaUpdateWrapper.set(SFunction, value) 在构造期立即解析列名，需要 TableInfo 缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WishUserStat.class);
    }

    @BeforeEach
    void setUp() {
        userStatService = new UserStatServiceImpl(wishUserStatMapper, wishResourceLogMapper);
        // initUserStat 的存在性检查默认无记录（允许 insert）
        when(wishUserStatMapper.selectById(USER_ID)).thenReturn(null);
    }

    @Nested
    @DisplayName("spendStarlight - 星光扣减")
    class SpendTests {

        @Test
        @DisplayName("正常扣减：条件 UPDATE 成功、流水 delta=-cost、返回最新余额")
        void spend_success() {
            when(wishUserStatMapper.update(any(), any())).thenReturn(1);
            WishUserStat after = buildStat(8);
            // initUserStat 存在性检查 + requireBalance 两次 selectById
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(null, after);

            int balance = userStatService.spendStarlight(
                    USER_ID, 2, ResourceLogSource.LIGHT_OTHER, 3001L);

            assertThat(balance).isEqualTo(8);
            ArgumentCaptor<WishResourceLog> captor = ArgumentCaptor.forClass(WishResourceLog.class);
            verify(wishResourceLogMapper).insert(captor.capture());
            WishResourceLog logEntry = captor.getValue();
            assertThat(logEntry.getDelta()).isEqualTo(-2);
            assertThat(logEntry.getType()).isEqualTo(ResourceLogType.SPEND);
            assertThat(logEntry.getBalanceAfter()).isEqualTo(8);
            assertThat(logEntry.getSource()).isEqualTo(ResourceLogSource.LIGHT_OTHER.name());
            assertThat(logEntry.getRefId()).isEqualTo(3001L);
        }

        @Test
        @DisplayName("余额不足（条件 UPDATE 影响 0 行）：抛 402 WISH_STARLIGHT_INSUFFICIENT")
        void spend_insufficient_402() {
            when(wishUserStatMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() -> userStatService.spendStarlight(
                    USER_ID, 2, ResourceLogSource.LIGHT_OTHER, null))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_STARLIGHT_INSUFFICIENT));
            verify(wishResourceLogMapper, never()).insert(any(WishResourceLog.class));
        }

        @Test
        @DisplayName("非法参数（cost<=0）：抛 IllegalArgumentException")
        void spend_invalidCost() {
            assertThatThrownBy(() -> userStatService.spendStarlight(
                    USER_ID, 0, ResourceLogSource.LIGHT_OTHER, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("earnStarlight - 星光发放")
    class EarnTests {

        @Test
        @DisplayName("正常发放：全额入账、流水 delta=amount")
        void earn_success() {
            when(wishUserStatMapper.selectOne(any())).thenReturn(buildStat(100));
            when(wishUserStatMapper.update(any(), any())).thenReturn(1);

            int credited = userStatService.earnStarlight(
                    USER_ID, 50, ResourceLogSource.LIGHTED, 3001L);

            assertThat(credited).isEqualTo(50);
            ArgumentCaptor<WishResourceLog> captor = ArgumentCaptor.forClass(WishResourceLog.class);
            verify(wishResourceLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getDelta()).isEqualTo(50);
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(150);
        }

        @Test
        @DisplayName("接近上限：截断入账（4990 + 20 → 仅入账 10）")
        void earn_cappedAt5000() {
            when(wishUserStatMapper.selectOne(any())).thenReturn(buildStat(4990));
            when(wishUserStatMapper.update(any(), any())).thenReturn(1);

            int credited = userStatService.earnStarlight(
                    USER_ID, 20, ResourceLogSource.LIGHTED, null);

            assertThat(credited).isEqualTo(10);
            ArgumentCaptor<WishResourceLog> captor = ArgumentCaptor.forClass(WishResourceLog.class);
            verify(wishResourceLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getDelta()).isEqualTo(10);
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(5000);
        }

        @Test
        @DisplayName("已达上限（5000）：入账 0 且不写流水")
        void earn_alreadyAtCap_noLog() {
            when(wishUserStatMapper.selectOne(any())).thenReturn(buildStat(5000));

            int credited = userStatService.earnStarlight(
                    USER_ID, 10, ResourceLogSource.LIGHTED, null);

            assertThat(credited).isZero();
            verify(wishResourceLogMapper, never()).insert(any(WishResourceLog.class));
            verify(wishUserStatMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("非法参数（amount<=0）：抛 IllegalArgumentException")
        void earn_invalidAmount() {
            assertThatThrownBy(() -> userStatService.earnStarlight(
                    USER_ID, -1, ResourceLogSource.LIGHTED, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("查询与统计")
    class QueryAndStatTests {

        @Test
        @DisplayName("余额查询：记录不存在返回 0")
        void balance_missingUser_returnsZero() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(null);
            assertThat(userStatService.getStarlightBalance(USER_ID)).isZero();
        }

        @Test
        @DisplayName("时区查询：记录缺失返回默认 Asia/Shanghai；存在返回用户时区")
        void timezone_defaultAndCustom() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(null);
            assertThat(userStatService.getUserTimezone(USER_ID)).isEqualTo("Asia/Shanghai");

            WishUserStat stat = buildStat(100);
            stat.setTimezone("America/New_York");
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(stat);
            assertThat(userStatService.getUserTimezone(USER_ID)).isEqualTo("America/New_York");
        }

        @Test
        @DisplayName("initUserStat 幂等：记录已存在不重复插入")
        void init_idempotent() {
            when(wishUserStatMapper.selectById(USER_ID)).thenReturn(buildStat(100));

            userStatService.initUserStat(USER_ID);

            verify(wishUserStatMapper, never()).insert(any(WishUserStat.class));

        }

        @Test
        @DisplayName("incrementTotalHelped：UPDATE 影响 0 行不抛异常（对账兜底）")
        void incrementHelped_missingUser_noException() {
            when(wishUserStatMapper.update(any(), any())).thenReturn(0);

            userStatService.incrementTotalHelped(USER_ID);

            verify(wishUserStatMapper).update(any(), any());
        }
    }

    private WishUserStat buildStat(int balance) {
        WishUserStat stat = new WishUserStat();
        stat.setUserId(USER_ID);
        stat.setTimezone("Asia/Shanghai");
        stat.setLevel((byte) 1);
        stat.setStarlightBalance(balance);
        stat.setTotalWishes(0);
        stat.setActiveWishes(0);
        stat.setTotalFulfilled(0);
        stat.setTotalHelped(0);
        stat.setTotalCheckinDays(0);
        stat.setLastActiveAt(LocalDateTime.now());
        stat.setIsRestricted(false);
        return stat;
    }
}
