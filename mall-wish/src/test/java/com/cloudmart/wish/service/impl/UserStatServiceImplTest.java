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
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.vo.LevelUpVO;
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
    @Mock
    private BadgeService badgeService;
    @Mock
    private com.cloudmart.wish.repository.WishPetOperationMapper wishPetOperationMapper;

    private UserStatServiceImpl userStatService;

    private static final Long USER_ID = 1001L;

    @BeforeAll
    static void initEntityMeta() {
        // LambdaUpdateWrapper.set(SFunction, value) 在构造期立即解析列名，需要 TableInfo 缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WishUserStat.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                com.cloudmart.wish.entity.WishPetOperation.class);
    }

    @BeforeEach
    void setUp() {
        userStatService = new UserStatServiceImpl(wishUserStatMapper, wishResourceLogMapper,
                wishPetOperationMapper, badgeService);
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
    @DisplayName("evaluateLevel - 等级判定（文档 6.5）")
    class LevelEvaluationTests {

        @Test
        @DisplayName("L1 默认：无任何行为指标")
        void evaluate_defaultL1() {
            assertThat(UserStatServiceImpl.evaluateLevel(0, 0, 0, 0)).isEqualTo(1);
        }

        @Test
        @DisplayName("L2 梦想家：许愿 ≥ 3 且打卡 ≥ 7（任一不足仍 L1）")
        void evaluate_l2() {
            assertThat(UserStatServiceImpl.evaluateLevel(3, 7, 0, 0)).isEqualTo(2);
            assertThat(UserStatServiceImpl.evaluateLevel(2, 7, 0, 0)).isEqualTo(1);
            assertThat(UserStatServiceImpl.evaluateLevel(3, 6, 0, 0)).isEqualTo(1);
        }

        @Test
        @DisplayName("L3 追光者：许愿 ≥ 10 且还愿 ≥ 1 且帮助 ≥ 50")
        void evaluate_l3() {
            assertThat(UserStatServiceImpl.evaluateLevel(10, 7, 1, 50)).isEqualTo(3);
            assertThat(UserStatServiceImpl.evaluateLevel(10, 99, 1, 49)).isEqualTo(2);
            assertThat(UserStatServiceImpl.evaluateLevel(10, 99, 0, 100)).isEqualTo(2);
        }

        @Test
        @DisplayName("L4 星火引路人：许愿 ≥ 30 且还愿 ≥ 5 且帮助 ≥ 200")
        void evaluate_l4() {
            assertThat(UserStatServiceImpl.evaluateLevel(30, 99, 5, 200)).isEqualTo(4);
            assertThat(UserStatServiceImpl.evaluateLevel(29, 99, 5, 300)).isEqualTo(3);
        }

        @Test
        @DisplayName("L5 宇宙守护者：许愿 ≥ 100 且还愿 ≥ 20 且帮助 ≥ 1000")
        void evaluate_l5() {
            assertThat(UserStatServiceImpl.evaluateLevel(100, 99, 20, 1000)).isEqualTo(5);
            assertThat(UserStatServiceImpl.evaluateLevel(100, 99, 20, 999)).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("checkAndLevelUp - 等级提升检测")
    class CheckAndLevelUpTests {

        @Test
        @DisplayName("达标升级：更新 level/highest_level/level_title 并返回事件")
        void levelUp_upgrade() {
            WishUserStat stat = buildStat(100);
            stat.setTotalWishes(3);
            stat.setTotalCheckinDays(7);
            stat.setHighestLevel((byte) 1);
            when(wishUserStatMapper.selectOne(any())).thenReturn(stat);
            when(wishUserStatMapper.update(any(), any())).thenReturn(1);

            LevelUpVO levelUp = userStatService.checkAndLevelUp(USER_ID);

            assertThat(levelUp).isNotNull();
            assertThat(levelUp.previousLevel()).isEqualTo(1);
            assertThat(levelUp.newLevel()).isEqualTo(2);
            assertThat(levelUp.newLevelTitle()).isEqualTo("梦想家");
        }

        @Test
        @DisplayName("未达标 / 已是最高满足等级：返回 null 不更新")
        void levelUp_notQualified() {
            WishUserStat stat = buildStat(100);
            stat.setTotalWishes(3);
            stat.setTotalCheckinDays(6);
            stat.setHighestLevel((byte) 1);
            when(wishUserStatMapper.selectOne(any())).thenReturn(stat);

            assertThat(userStatService.checkAndLevelUp(USER_ID)).isNull();
            verify(wishUserStatMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("统计记录不存在：返回 null（首次签到等场景由 earnStarlight 先初始化）")
        void levelUp_missingStat() {
            when(wishUserStatMapper.selectOne(any())).thenReturn(null);

            assertThat(userStatService.checkAndLevelUp(USER_ID)).isNull();
        }

        @Test
        @DisplayName("指标回落（highest_level 已为 2，当前指标仅满足 L1）：只升不降，返回 null")
        void levelUp_neverDowngrade() {
            WishUserStat stat = buildStat(100);
            stat.setTotalWishes(1);
            stat.setTotalCheckinDays(2);
            stat.setLevel((byte) 2);
            stat.setHighestLevel((byte) 2);
            when(wishUserStatMapper.selectOne(any())).thenReturn(stat);

            assertThat(userStatService.checkAndLevelUp(USER_ID)).isNull();
            verify(wishUserStatMapper, never()).update(any(), any());
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

    @Nested
    @DisplayName("refundStarlightIdempotent - 宠物旧单退款（P02/TX-04）")
    class RefundTests {

        private com.cloudmart.wish.entity.WishPetOperation originalSpend(int credited) {
            com.cloudmart.wish.entity.WishPetOperation original = new com.cloudmart.wish.entity.WishPetOperation();
            original.setOperationId("orig-1");
            original.setUserId(USER_ID);
            original.setOperationType(ResourceLogType.SPEND.name());
            original.setAmount(credited);
            original.setCreditedAmount(credited);
            original.setRefId(3001L);
            return original;
        }

        @Test
        @DisplayName("正常退款：等于实扣金额全额入账，不受余额上限截断，PET_REFUND 流水")
        void refund_success_bypassesCap() {
            when(wishPetOperationMapper.insert(any(com.cloudmart.wish.entity.WishPetOperation.class))).thenReturn(1);
            when(wishPetOperationMapper.selectOne(any())).thenReturn(originalSpend(20));
            when(wishPetOperationMapper.selectList(any())).thenReturn(java.util.List.of());
            when(wishUserStatMapper.update(any(), any())).thenReturn(1);
            // 余额已在 4990（接近 5000 上限）：退款仍须全额 20 入账
            when(wishUserStatMapper.selectOne(any())).thenReturn(buildStat(4990));

            com.cloudmart.wish.vo.PetWalletOperationVO vo = userStatService.refundStarlightIdempotent(
                    USER_ID, 20, "orig-1", "refund-1");

            assertThat(vo.creditedAmount()).isEqualTo(20);
            assertThat(vo.balanceAfter()).isEqualTo(5010);
            ArgumentCaptor<WishResourceLog> captor = ArgumentCaptor.forClass(WishResourceLog.class);
            verify(wishResourceLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getDelta()).isEqualTo(20);
            assertThat(captor.getValue().getSource()).isEqualTo(ResourceLogSource.PET_REFUND.name());
            ArgumentCaptor<com.cloudmart.wish.entity.WishPetOperation> opCaptor =
                    ArgumentCaptor.forClass(com.cloudmart.wish.entity.WishPetOperation.class);
            verify(wishPetOperationMapper).updateById(opCaptor.capture());
            assertThat(opCaptor.getValue().getRefundOfOperationId()).isEqualTo("orig-1");
            assertThat(opCaptor.getValue().getCreditedAmount()).isEqualTo(20);
        }

        @Test
        @DisplayName("原单不存在：409 冲突，不写流水")
        void refund_originalMissing_conflict() {
            when(wishPetOperationMapper.insert(any(com.cloudmart.wish.entity.WishPetOperation.class))).thenReturn(1);
            when(wishPetOperationMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> userStatService.refundStarlightIdempotent(
                    USER_ID, 20, "orig-missing", "refund-1"))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_OPERATION_CONFLICT));
            verify(wishResourceLogMapper, never()).insert(any(WishResourceLog.class));
        }

        @Test
        @DisplayName("原单不属于该用户：409 冲突（越权退款拒绝）")
        void refund_notOwner_conflict() {
            com.cloudmart.wish.entity.WishPetOperation other = originalSpend(20);
            other.setUserId(9999L);
            when(wishPetOperationMapper.insert(any(com.cloudmart.wish.entity.WishPetOperation.class))).thenReturn(1);
            when(wishPetOperationMapper.selectOne(any())).thenReturn(other);

            assertThatThrownBy(() -> userStatService.refundStarlightIdempotent(
                    USER_ID, 20, "orig-1", "refund-1"))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_OPERATION_CONFLICT));
        }

        @Test
        @DisplayName("非全额退款（首版不支持部分退）：409 冲突")
        void refund_partialAmount_conflict() {
            when(wishPetOperationMapper.insert(any(com.cloudmart.wish.entity.WishPetOperation.class))).thenReturn(1);
            when(wishPetOperationMapper.selectOne(any())).thenReturn(originalSpend(20));

            assertThatThrownBy(() -> userStatService.refundStarlightIdempotent(
                    USER_ID, 10, "orig-1", "refund-1"))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_OPERATION_CONFLICT));
        }

        @Test
        @DisplayName("累计退款超过原单实扣：409 冲突")
        void refund_cumulativeExceeded_conflict() {
            com.cloudmart.wish.entity.WishPetOperation prior = new com.cloudmart.wish.entity.WishPetOperation();
            prior.setCreditedAmount(20);
            when(wishPetOperationMapper.insert(any(com.cloudmart.wish.entity.WishPetOperation.class))).thenReturn(1);
            when(wishPetOperationMapper.selectOne(any())).thenReturn(originalSpend(20));
            when(wishPetOperationMapper.selectList(any())).thenReturn(java.util.List.of(prior));

            assertThatThrownBy(() -> userStatService.refundStarlightIdempotent(
                    USER_ID, 20, "orig-1", "refund-2"))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getCode()).isEqualTo(WishErrorCodes.WISH_OPERATION_CONFLICT));
        }

        @Test
        @DisplayName("重复同键退款请求：返回原结果（duplicate）")
        void refund_duplicateKey_returnsOriginal() {
            java.util.concurrent.atomic.AtomicReference<String> digestRef =
                    new java.util.concurrent.atomic.AtomicReference<>();
            when(wishPetOperationMapper.insert(any(com.cloudmart.wish.entity.WishPetOperation.class)))
                    .thenAnswer(inv -> {
                        digestRef.set(((com.cloudmart.wish.entity.WishPetOperation) inv.getArgument(0))
                                .getRequestDigest());
                        throw new org.springframework.dao.DuplicateKeyException("dup");
                    });
            when(wishPetOperationMapper.selectOne(any())).thenAnswer(inv -> {
                com.cloudmart.wish.entity.WishPetOperation existing = new com.cloudmart.wish.entity.WishPetOperation();
                existing.setOperationId("refund-1");
                existing.setRequestDigest(digestRef.get());
                existing.setAmount(20);
                existing.setCreditedAmount(20);
                return existing;
            });

            com.cloudmart.wish.vo.PetWalletOperationVO vo = userStatService.refundStarlightIdempotent(
                    USER_ID, 20, "orig-1", "refund-1");

            assertThat(vo.duplicate()).isTrue();
            assertThat(vo.creditedAmount()).isEqualTo(20);
        }
    }
}
