package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetPveParticipant;
import com.cloudmart.pet.entity.PetPveRoundLog;
import com.cloudmart.pet.entity.PetPveRun;
import com.cloudmart.pet.entity.PetRewardClaim;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetPveParticipantMapper;
import com.cloudmart.pet.repository.PetPveRoundLogMapper;
import com.cloudmart.pet.repository.PetPveRunMapper;
import com.cloudmart.pet.repository.PetRewardClaimMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetPveRunVO;
import com.cloudmart.pet.wallet.PetRewardApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R03/R04：PVE 轮次状态机（nextActor 判定/CAS 推进/占用行唯一/重放）与可恢复奖励
 * （PENDING→逐用户 claim→SETTLED；配额外不发币；独立 PVE_REWARD 配额）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetPveServiceImpl 单元测试（R03/R04）")
class PetPveServiceImplTest {

    private static final Long INITIATOR = 11L;
    private static final Long PARTNER = 22L;
    private static final Long RUN_ID = 100L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private PetPveRunMapper runMapper;
    @Mock
    private PetPveParticipantMapper participantMapper;
    @Mock
    private PetPveRoundLogMapper roundLogMapper;
    @Mock
    private PetRewardClaimMapper rewardClaimMapper;
    @Mock
    private PetService petService;
    @Mock
    private PetMapper petMapper;
    @Mock
    private PetStatsService statsService;
    @Mock
    private PetQuotaService quotaService;
    @Mock
    private PetRewardApplicationService rewardApplicationService;

    private PetPveServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        for (Class<?> clazz : new Class<?>[]{PetPveRun.class, PetPveParticipant.class,
                PetPveRoundLog.class, PetRewardClaim.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                TableInfoHelper.initTableInfo(
                        new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        service = new PetPveServiceImpl(runMapper, participantMapper, roundLogMapper,
                rewardClaimMapper, petService, petMapper, statsService, quotaService,
                rewardApplicationService);
        service.rewardCoins = 100;
        service.pveDailyRewardLimit = 3;
        service.runTimeLimitMinutes = 120;
        lenient().when(roundLogMapper.selectCount(any())).thenReturn(0L);
        lenient().when(roundLogMapper.selectList(any())).thenReturn(List.of());
        lenient().when(participantMapper.selectCount(any())).thenReturn(0L);
    }

    // ---------------- 造数 ----------------

    private PetPveRun fightingRun(int version, int turnNo, Long nextActor) {
        PetPveRun run = new PetPveRun();
        run.setId(RUN_ID);
        run.setBossCode("forest_ogre");
        run.setBossName("森林巨魔");
        run.setBossMaxHp(600);
        run.setBossHp(600);
        run.setInitiatorUserId(INITIATOR);
        run.setInitiatorPetId(1L);
        run.setInitiatorSnapshot(fighterJson("发起人宠", 200));
        run.setInitiatorPetHp(200);
        run.setPartnerUserId(PARTNER);
        run.setPartnerPetId(2L);
        run.setPartnerSnapshot(fighterJson("队友宠", 180));
        run.setPartnerPetHp(180);
        run.setStatus(PetPveRun.STATUS_FIGHTING);
        run.setVersion(version);
        run.setTurnNo(turnNo);
        run.setNextActorUserId(nextActor);
        run.setRuleVersion("V1");
        run.setSeed(42L);
        run.setRewardStatus(PetPveRun.REWARD_NONE);
        return run;
    }

    private String fighterJson(String name, int hp) {
        try {
            return JSON.writeValueAsString(new PetBattleEngine.Fighter(1L, name, hp, hp,
                    40, 30, 25, 20, 0.0, 0.0, 5, 0.0, 0.0, 0));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Pet ownedPet(long petId, String name, int hp) {
        Pet pet = new Pet();
        pet.setId(petId);
        pet.setName(name);
        pet.setUserId(INITIATOR);
        lenient().when(statsService.combatStats(any())).thenReturn(
                new PetStatsService.CombatStats(hp, hp, 40, 30, 25, 20, 0.0, 0.0, 5, 0.0, 0.0));
        lenient().when(statsService.firstStrikeBonus(any())).thenReturn(0);
        return pet;
    }

    @Nested
    @DisplayName("R03：轮次状态机")
    class StateMachineTests {

        @Test
        @DisplayName("发起人首攻 → turnNo=1、行动权交队友、回合明细落独立表（seq 1-2）")
        void attack_initiatorFirstTurn_passesToPartner() {
            PetPveRun run = fightingRun(1, 0, INITIATOR);
            when(runMapper.selectById(RUN_ID)).thenReturn(run);
            when(runMapper.update(any(), any(LambdaUpdateWrapper.class))).thenAnswer(inv -> {
                // CAS 成功：模拟 run 行状态推进
                run.setTurnNo(1);
                run.setNextActorUserId(PARTNER);
                run.setVersion(2);
                run.setBossHp(600 - 30);
                return 1;
            });

            PetPveRunVO vo = service.attack(INITIATOR, RUN_ID, "atk-1");

            verify(roundLogMapper, times(2)).insert(any(PetPveRoundLog.class));
            assertThat(vo.turnNo()).isEqualTo(1);
            assertThat(vo.nextActorUserId()).isEqualTo(PARTNER);
            assertThat(vo.canAttack()).isFalse(); // 视角=发起人，已交棒
            assertThat(vo.blockedReason()).isEqualTo("NOT_YOUR_TURN");
        }

        @Test
        @DisplayName("非行动方攻击 → 拒绝（nextActorUserId 权威，日志条数不再决定轮次）")
        void attack_notYourTurn_rejected() {
            PetPveRun run = fightingRun(1, 1, PARTNER);
            when(runMapper.selectById(RUN_ID)).thenReturn(run);

            assertThatThrownBy(() -> service.attack(INITIATOR, RUN_ID, "atk-2"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
            verify(runMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("同回合并发：CAS 0 行 → PET_STATE_CONFLICT（回合明细随事务回滚）")
        void attack_casConflict_rejected() {
            PetPveRun run = fightingRun(1, 0, INITIATOR);
            when(runMapper.selectById(RUN_ID)).thenReturn(run);
            when(runMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> service.attack(INITIATOR, RUN_ID, "atk-3"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_STATE_CONFLICT);
        }

        @Test
        @DisplayName("同 requestKey 重复攻击 → 返回原战局（不重复推进不重复落日志）")
        void attack_sameRequestKey_replays() {
            PetPveRun run = fightingRun(2, 1, PARTNER);
            when(runMapper.selectById(RUN_ID)).thenReturn(run);
            when(roundLogMapper.selectCount(any())).thenReturn(1L);

            service.attack(INITIATOR, RUN_ID, "atk-1");

            verify(runMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
            verify(roundLogMapper, never()).insert(any(PetPveRoundLog.class));
        }

        @Test
        @DisplayName("并发 start：占用行 uk 冲突 → 明确拒绝（一人一个活动副本）")
        void start_participantOccupied_rejected() {
            Pet owned = ownedPet(1L, "发起人宠", 200);
            when(petService.requireOwnedPet(INITIATOR)).thenReturn(owned);
            when(participantMapper.insert(any(PetPveParticipant.class)))
                    .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_user_active"));

            assertThatThrownBy(() -> service.start(INITIATOR, "forest_ogre"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("进行中的副本");
        }

        @Test
        @DisplayName("并发 join：CAS OPEN→FIGHTING 0 行 → 释放自己占用并拒绝（两场只占一场）")
        void join_casFailure_releasesOccupation() {
            PetPveRun run = fightingRun(0, 0, null);
            run.setStatus(PetPveRun.STATUS_OPEN);
            run.setPartnerUserId(null);
            run.setPartnerPetId(null);
            run.setPartnerSnapshot(null);
            run.setPartnerPetHp(null);
            when(runMapper.selectById(RUN_ID)).thenReturn(run);
            when(runMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);
            when(participantMapper.insert(any(PetPveParticipant.class))).thenAnswer(inv -> {
                inv.getArgument(0, PetPveParticipant.class).setId(9L);
                return 1;
            });
            Pet partnerPet = ownedPet(2L, "队友宠", 180);
            when(petService.requireOwnedPet(PARTNER)).thenReturn(partnerPet);

            assertThatThrownBy(() -> service.join(PARTNER, RUN_ID))
                    .isInstanceOf(BusinessException.class);
            verify(participantMapper).deleteById(any(java.io.Serializable.class));
        }

        @Test
        @DisplayName("WON → reward_status=PENDING；终态释放参与占用")
        void attack_bossDown_wonPendingRewardsAndRelease() {
            PetPveRun run = fightingRun(1, 0, INITIATOR);
            run.setBossHp(20); // 一击可倒
            when(runMapper.selectById(RUN_ID)).thenReturn(run);
            when(runMapper.update(any(), any(LambdaUpdateWrapper.class))).thenAnswer(inv -> {
                run.setStatus(PetPveRun.STATUS_WON);
                run.setRewardStatus(PetPveRun.REWARD_PENDING);
                run.setNextActorUserId(null);
                run.setFinishedAt(LocalDateTime.now());
                return 1;
            });

            PetPveRunVO vo = service.attack(INITIATOR, RUN_ID, "atk-win");

            assertThat(vo.status()).isEqualTo("WON");
            assertThat(vo.rewardStatus()).isEqualTo("PENDING");
            assertThat(vo.rewardGranted()).isFalse(); // 交付未完成前不得宣称全部到账
            verify(participantMapper).delete(any());
        }

        @Test
        @DisplayName("过期 OPEN/FIGHTING 收口：批量置终态并释放占用")
        void closeExpiredRuns_transitionsAndReleases() {
            PetPveRun fighting = fightingRun(1, 0, INITIATOR);
            fighting.setRunExpiresAt(LocalDateTime.now().minusMinutes(1));
            when(runMapper.selectList(any())).thenReturn(List.of(fighting));
            when(runMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            int closed = service.closeExpiredRuns();

            assertThat(closed).isGreaterThanOrEqualTo(1);
            verify(participantMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("R04：可恢复奖励")
    class RewardTests {

        @Test
        @DisplayName("结算：双方各自 claim（bizId=runId、归属各自宠物）；全部完成 → SETTLED")
        void settlePendingRewards_allCompleted() {
            PetPveRun won = fightingRun(2, 3, null);
            won.setStatus(PetPveRun.STATUS_WON);
            won.setRewardStatus(PetPveRun.REWARD_PENDING);
            when(runMapper.selectList(any())).thenReturn(List.of(won));
            when(rewardApplicationService.claim(any(), any()))
                    .thenReturn(new PetRewardApplicationService.RewardClaimResult(
                            "c1", 100, 100, "op1", List.of(), false));
            when(rewardClaimMapper.selectOne(any())).thenReturn(completedClaim());

            int settled = service.settlePendingRewards();

            assertThat(settled).isEqualTo(1);
            ArgumentCaptor<PetRewardApplicationService.RewardClaimCommand> commandCaptor =
                    ArgumentCaptor.forClass(PetRewardApplicationService.RewardClaimCommand.class);
            verify(rewardApplicationService, times(2)).claim(commandCaptor.capture(), any());
            // R04：每人各自宠物归属（不再都用发起人 petId）
            assertThat(commandCaptor.getAllValues()).extracting(
                    PetRewardApplicationService.RewardClaimCommand::petId).containsExactly(1L, 2L);
            verify(runMapper).update(any(), any(LambdaUpdateWrapper.class)); // SETTLED 回写
        }

        @Test
        @DisplayName("一方 claim 异常（钱包冻结）→ 副本留 PENDING 可恢复，不虚报全部到账")
        void settlePendingRewards_partialFailure_staysPending() {
            PetPveRun won = fightingRun(2, 3, null);
            won.setStatus(PetPveRun.STATUS_WON);
            won.setRewardStatus(PetPveRun.REWARD_PENDING);
            when(runMapper.selectList(any())).thenReturn(List.of(won));
            when(rewardApplicationService.claim(any(), any()))
                    .thenReturn(new PetRewardApplicationService.RewardClaimResult(
                            "c1", 100, 100, "op1", List.of(), false))
                    .thenThrow(new BusinessException("PET_WALLET_FROZEN", "钱包冻结"));
            when(rewardClaimMapper.selectOne(any())).thenReturn(completedClaim());

            int settled = service.settlePendingRewards();

            assertThat(settled).isZero();
            verify(runMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("配额外（第 4 次胜利）：resolver 发 0 币（仅参与），不占串门配额")
        void settleRewards_overQuota_zeroCoins() {
            when(quotaService.tryConsume(anyLong(), eq(PetQuotaService.QuotaType.PVE_REWARD),
                    anyLong(), eq(3))).thenReturn(false);
            when(quotaService.tryConsume(anyLong(), eq(PetQuotaService.QuotaType.VISIT_REWARD),
                    anyLong(), eq(3))).thenReturn(true);
            when(rewardApplicationService.claim(any(), any()))
                    .thenReturn(new PetRewardApplicationService.RewardClaimResult(
                            "c1", 0, 50, "op1", List.of(), false));
            when(rewardClaimMapper.selectOne(any())).thenReturn(completedClaim());
            PetPveRun won = fightingRun(2, 3, null);
            won.setStatus(PetPveRun.STATUS_WON);
            won.setRewardStatus(PetPveRun.REWARD_PENDING);
            when(runMapper.selectList(any())).thenReturn(List.of(won));

            service.settlePendingRewards();

            ArgumentCaptor<PetRewardApplicationService.RewardResolver> resolverCaptor =
                    ArgumentCaptor.forClass(PetRewardApplicationService.RewardResolver.class);
            verify(rewardApplicationService, times(2)).claim(any(), resolverCaptor.capture());
            // 配额外不发币（无保底参与奖）
            assertThat(resolverCaptor.getAllValues().stream()
                    .map(r -> r.resolve(null).coin()).allMatch(c -> c == 0)).isTrue();
            // 串门配额不受影响（VISIT_REWARD 未被调用）
            verify(quotaService, never()).tryConsume(anyLong(),
                    eq(PetQuotaService.QuotaType.VISIT_REWARD), anyLong(), eq(3));
        }

        private PetRewardClaim completedClaim() {
            PetRewardClaim claim = new PetRewardClaim();
            claim.setId(1L);
            claim.setStatus("COMPLETED");
            return claim;
        }
    }
}
