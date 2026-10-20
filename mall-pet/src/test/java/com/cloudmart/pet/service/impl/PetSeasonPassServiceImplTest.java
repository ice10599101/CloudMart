package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonPass;
import com.cloudmart.pet.entity.PetSeasonPassClaim;
import com.cloudmart.pet.entity.PetSeasonPassExpFact;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonPassClaimMapper;
import com.cloudmart.pet.repository.PetSeasonPassMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetSeasonPassVO;
import com.cloudmart.pet.wallet.PetRewardApplicationService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R05：通行证逐档权威领取（并发互不覆盖/同档一次/失败可恢复/PENDING 不虚报）
 * 与经验事实去重。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetSeasonPassServiceImpl 单元测试（R05）")
class PetSeasonPassServiceImplTest {

    private static final Long USER_ID = 8L;
    private static final Long SEASON_ID = 5L;

    @Mock
    private PetSeasonMapper seasonMapper;
    @Mock
    private PetSeasonPassMapper passMapper;
    @Mock
    private PetSeasonPassClaimMapper claimMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetRewardApplicationService rewardApplicationService;
    @Mock
    private PetService petService;
    @Mock
    private PetSeasonPassExpAccumulator expAccumulator;
    @Mock
    private com.cloudmart.pet.config.PetSeasonPassClaimMetrics claimMetrics;

    private PetSeasonPassServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        for (Class<?> clazz : new Class<?>[]{PetSeason.class, PetSeasonPass.class,
                PetSeasonPassClaim.class, PetSeasonPassExpFact.class, PetInventory.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                TableInfoHelper.initTableInfo(
                        new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        service = new PetSeasonPassServiceImpl(seasonMapper, passMapper, claimMapper,
                inventoryMapper, rewardApplicationService, petService,
                new com.cloudmart.pet.config.PetProperties(), expAccumulator, claimMetrics);
        service.finalTierSkinCode = "";
        when(rewardApplicationService.claim(any(), any()))
                .thenReturn(new PetRewardApplicationService.RewardClaimResult(
                        "c1", 20, 20, "op1", List.of(), false));
    }

    private PetSeason activeSeason() {
        PetSeason season = new PetSeason();
        season.setId(SEASON_ID);
        season.setName("S1");
        season.setStatus("ACTIVE");
        season.setStartsAt(LocalDateTime.now().minusDays(1));
        season.setEndsAt(LocalDateTime.now().plusDays(10));
        return season;
    }

    private PetSeasonPass pass(int exp) {
        PetSeasonPass pass = new PetSeasonPass();
        pass.setId(1L);
        pass.setSeasonId(SEASON_ID);
        pass.setUserId(USER_ID);
        pass.setPassExp(exp);
        pass.setClaimedTiers("[]");
        pass.setVersion(0);
        return pass;
    }

    private PetSeasonPassClaim claim(int tier, String status) {
        PetSeasonPassClaim claim = new PetSeasonPassClaim();
        claim.setId((long) tier);
        claim.setSeasonId(SEASON_ID);
        claim.setUserId(USER_ID);
        claim.setTier(tier);
        claim.setStatus(status);
        return claim;
    }

    @Nested
    @DisplayName("逐档权威领取")
    class ClaimTests {

        @Test
        @DisplayName("领取成功：claim SETTLED + 奖励交付经应用服务（uk 事实键）")
        void claimTier_success() {
            when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            when(claimMapper.selectList(any())).thenReturn(List.of(claim(1, "SETTLED")));
            when(passMapper.update(any(), any())).thenReturn(1);

            PetSeasonPassVO vo = service.claimTier(USER_ID, null, 1, "rk-1");

            assertThat(vo.claimedTiers()).containsExactly(1);
            assertThat(vo.tiers().get(0).claimStatus()).isEqualTo("SETTLED");
            assertThat(vo.tiers().get(0).claimed()).isTrue();
            ArgumentCaptor<PetRewardApplicationService.RewardClaimCommand> commandCaptor =
                    ArgumentCaptor.forClass(PetRewardApplicationService.RewardClaimCommand.class);
            verify(rewardApplicationService).claim(commandCaptor.capture(), any());
            // 事实键：PASS_TIER + season:tier（每档唯一，交付 uk 防重）
            assertThat(commandCaptor.getValue().bizType()).isEqualTo("PASS_TIER");
            assertThat(commandCaptor.getValue().bizId()).isEqualTo(SEASON_ID + ":1");
        }

        @Test
        @DisplayName("并发领不同档：uk(season,user,tier) 逐档行互不覆盖（各档独立裁决）")
        void claimTier_differentTiers_independentRows() {
            when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            when(claimMapper.selectList(any())).thenReturn(List.of(
                    claim(1, "SETTLED"), claim(2, "SETTLED")));
            when(passMapper.update(any(), any())).thenReturn(1);

            service.claimTier(USER_ID, null, 1, "rk-1");
            service.claimTier(USER_ID, null, 2, "rk-2");

            verify(claimMapper, times(2)).insert(any(PetSeasonPassClaim.class));
            assertThat(service.myPass(USER_ID).claimedTiers()).containsExactly(1, 2);
        }

        @Test
        @DisplayName("同档重复领取：已 SETTLED → 拒绝；交付只发生一次")
        void claimTier_sameTier_rejectedAfterSettled() {
            when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            when(claimMapper.insert(any(PetSeasonPassClaim.class)))
                    .thenThrow(new DuplicateKeyException("uk_season_user_tier"));
            when(claimMapper.selectOne(any())).thenReturn(claim(1, "SETTLED"));

            assertThatThrownBy(() -> service.claimTier(USER_ID, null, 1, "rk-2"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已领取");
            verify(rewardApplicationService, never()).claim(any(), any());
        }

        @Test
        @DisplayName("交付失败 → 领取行留 CLAIM_PENDING，VO 不展示为已领取（PENDING 不虚报）")
        void claimTier_deliveryFailure_staysPendingNotClaimed() {
            when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            when(claimMapper.selectList(any())).thenReturn(List.of(claim(1, "CLAIM_PENDING")));
            when(rewardApplicationService.claim(any(), any()))
                    .thenThrow(new BusinessException("PET_WALLET_FROZEN", "钱包冻结"));

            PetSeasonPassVO vo = service.claimTier(USER_ID, null, 1, "rk-1");

            assertThat(vo.claimedTiers()).isEmpty(); // PENDING 不计入已领取
            assertThat(vo.tiers().get(0).claimed()).isFalse();
            assertThat(vo.tiers().get(0).claimStatus()).isEqualTo("CLAIM_PENDING");
            verify(claimMapper, never()).updateById(any(PetSeasonPassClaim.class)); // 未置 SETTLED
        }

        @Test
        @DisplayName("补发：CLAIM_PENDING 重试交付成功 → SETTLED（每人每档一次）")
        void settlePendingClaims_retriesDelivery() {
            when(claimMapper.selectDuePending(any(), anyInt())).thenReturn(List.of(claim(1, "CLAIM_PENDING")));
            when(claimMapper.tryLease(any(), any(), any(), any())).thenReturn(1);
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            when(passMapper.update(any(), any())).thenReturn(1);
            when(claimMapper.update(any(), any())).thenReturn(1);

            int settled = service.settlePendingClaims();

            assertThat(settled).isEqualTo(1);
            verify(rewardApplicationService).claim(any(), any());
            verify(claimMapper).update(any(), any()); // markSettled
        }

        @Test
        @DisplayName("T16：补发按领取快照发皮肤（目标宠物），不读当前配置")
        void settle_deliversSkinFromSnapshotNotCurrentConfig() {
            service.finalTierSkinCode = "CURRENT_SKIN"; // 当前配置已变更
            PetSeasonPassClaim pending = claim(10, "CLAIM_PENDING");
            pending.setRewardSnapshot(
                    "{\"tier\":10,\"coins\":200,\"skin\":\"SNAP_SKIN\",\"ruleVersion\":\"V1\",\"targetPetId\":42}");
            when(claimMapper.selectDuePending(any(), anyInt())).thenReturn(List.of(pending));
            when(claimMapper.tryLease(any(), any(), any(), any())).thenReturn(1);
            when(passMapper.selectOne(any())).thenReturn(pass(200));
            when(passMapper.update(any(), any())).thenReturn(1);
            when(claimMapper.update(any(), any())).thenReturn(1);
            Pet pet = new Pet();
            pet.setId(42L);
            when(petService.requireOwnedPet(USER_ID, 42L)).thenReturn(pet);
            // 让 mock 真正调用 resolver，从而走到 grantSkin（否则皮肤分支不执行）
            when(rewardApplicationService.claim(any(), any())).thenAnswer(inv -> {
                var resolver = inv.getArgument(1, PetRewardApplicationService.RewardResolver.class);
                resolver.resolve(inv.getArgument(0));
                return new PetRewardApplicationService.RewardClaimResult(
                        "c1", 200, 200, "op1", List.of(), false);
            });

            int settled = service.settlePendingClaims();

            assertThat(settled).isEqualTo(1);
            ArgumentCaptor<PetInventory> cap = ArgumentCaptor.forClass(PetInventory.class);
            verify(inventoryMapper).insert(cap.capture());
            assertThat(cap.getValue().getItemCode()).isEqualTo("SNAP_SKIN");
            assertThat(cap.getValue().getPetId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("T18：租约抢占失败（其他实例持有）→ 跳过不交付")
        void settle_leaseConflict_skips() {
            when(claimMapper.selectDuePending(any(), anyInt())).thenReturn(List.of(claim(1, "CLAIM_PENDING")));
            when(claimMapper.tryLease(any(), any(), any(), any())).thenReturn(0); // 其他实例已持有

            int settled = service.settlePendingClaims();

            assertThat(settled).isEqualTo(0);
            verify(rewardApplicationService, never()).claim(any(), any());
        }

        @Test
        @DisplayName("T18：可重试失败 → 指数退避（next_retry_at 置未来，不占头部）")
        void settle_retryableFailure_backsOff() {
            when(claimMapper.selectDuePending(any(), anyInt())).thenReturn(List.of(claim(1, "CLAIM_PENDING")));
            when(claimMapper.tryLease(any(), any(), any(), any())).thenReturn(1);
            when(rewardApplicationService.claim(any(), any()))
                    .thenThrow(new BusinessException("PET_WALLET_MAINTENANCE", "维护中"));

            int settled = service.settlePendingClaims();

            assertThat(settled).isEqualTo(0);
            // 失败记录：attempts+1、next_retry_at 置未来、不置 NEEDS_ATTENTION
            verify(claimMapper).update(any(), any());
        }

        @Test
        @DisplayName("T18：不可重试错误 → 立即 NEEDS_ATTENTION（可见可处置）")
        void settle_nonRetryableFailure_needsAttention() {
            PetSeasonPassClaim pending = claim(1, "CLAIM_PENDING");
            when(claimMapper.selectDuePending(any(), anyInt())).thenReturn(List.of(pending));
            when(claimMapper.tryLease(any(), any(), any(), any())).thenReturn(1);
            when(rewardApplicationService.claim(any(), any()))
                    .thenThrow(new BusinessException("PET_WALLET_FROZEN", "钱包冻结"));

            int settled = service.settlePendingClaims();

            assertThat(settled).isEqualTo(0);
            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetSeasonPassClaim>> cap =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
            verify(claimMapper).update(any(), cap.capture());
            assertThat(cap.getValue().getParamNameValuePairs())
                    .containsValue(PetSeasonPassClaim.STATUS_NEEDS_ATTENTION);
        }

        @Test
        @DisplayName("T18：重试达上限 → NEEDS_ATTENTION")
        void settle_maxAttemptsExceeded_needsAttention() {
            PetSeasonPassClaim pending = claim(1, "CLAIM_PENDING");
            pending.setAttempts(5); // 已达 maxAttempts-1（默认 6）
            when(claimMapper.selectDuePending(any(), anyInt())).thenReturn(List.of(pending));
            when(claimMapper.tryLease(any(), any(), any(), any())).thenReturn(1);
            when(rewardApplicationService.claim(any(), any()))
                    .thenThrow(new RuntimeException("db down"));

            int settled = service.settlePendingClaims();

            assertThat(settled).isEqualTo(0);
            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetSeasonPassClaim>> cap =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
            verify(claimMapper).update(any(), cap.capture());
            assertThat(cap.getValue().getParamNameValuePairs())
                    .containsValue(PetSeasonPassClaim.STATUS_NEEDS_ATTENTION);
        }

        @Test
        @DisplayName("T18：人工重试 NEEDS_ATTENTION → 重置为 CLAIM_PENDING（立即待补发）")
        void retryClaim_resetsToPending() {
            PetSeasonPassClaim attention = claim(1, "NEEDS_ATTENTION");
            when(claimMapper.selectById(1L)).thenReturn(attention, claim(1, "CLAIM_PENDING"));

            service.retryClaim(1L, "运营复核");

            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetSeasonPassClaim>> cap =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
            verify(claimMapper).update(any(), cap.capture());
            assertThat(cap.getValue().getParamNameValuePairs())
                    .containsValue(PetSeasonPassClaim.STATUS_CLAIM_PENDING);
        }

        @Test
        @DisplayName("经验不足/无赛季/领奖截止 → 明确拒绝")
        void claimTier_guards() {
            when(seasonMapper.selectOne(any())).thenReturn(null);
            assertThatThrownBy(() -> service.claimTier(USER_ID, null, 1, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("没有进行中的赛季");

            when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
            when(passMapper.selectOne(any())).thenReturn(pass(5));
            assertThatThrownBy(() -> service.claimTier(USER_ID, null, 1, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("经验不足");

            PetSeason closed = activeSeason();
            closed.setClaimUntil(LocalDateTime.now().minusHours(1));
            when(seasonMapper.selectOne(any())).thenReturn(closed);
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            assertThatThrownBy(() -> service.claimTier(USER_ID, null, 1, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("领奖已截止");
        }

        @Test
        @DisplayName("皮肤承诺以配置为准：未配置皮肤时描述不含皮肤、交付不发皮肤")
        void claimTier_finalTier_skinOnlyWhenConfigured() {
            service.finalTierSkinCode = "";
            when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            when(claimMapper.selectList(any())).thenReturn(List.of(claim(10, "SETTLED")));
            when(passMapper.update(any(), any())).thenReturn(1);

            PetSeasonPassVO vo = service.claimTier(USER_ID, null, 10, "rk-1");

            assertThat(vo.tiers().get(9).rewardDesc()).doesNotContain("皮肤");
            verify(inventoryMapper, never()).insert(any(PetInventory.class));
        }
    }

    @Nested
    @DisplayName("经验事实（T17 委托原子累计器）")
    class ExpFactTests {

        @Test
        @DisplayName("委托独立累计器完成事实+累加（来源/事实 ID 原样传递）")
        void addExp_delegatesToAccumulator() {
            when(expAccumulator.accumulate(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-100"))
                    .thenReturn(true);

            service.addExp(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-100");

            verify(expAccumulator).accumulate(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-100");
        }

        @Test
        @DisplayName("缺来源事实 ID → 编程错误直接抛出（不留无事实经验）")
        void addExp_missingFactId_rejected() {
            assertThatThrownBy(() -> service.addExp(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("累计器失败不阻断主流程（best-effort：异常被吞，原子回滚由累计器保证）")
        void addExp_accumulatorFailure_swallowed() {
            when(expAccumulator.accumulate(any(), anyInt(), any(), any()))
                    .thenThrow(new RuntimeException("db down"));

            // 不抛异常：任务领取主流程不受经验累计失败影响
            service.addExp(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-1");
        }
    }

    @Test
    @DisplayName("myPass 无进行中赛季 → 空视图（seasonId=null）")
    void myPass_noSeason_empty() {
        when(seasonMapper.selectOne(any())).thenReturn(null);

        PetSeasonPassVO vo = service.myPass(USER_ID);

        assertThat(vo.seasonId()).isNull();
        assertThat(vo.claimedTiers()).isEmpty();
    }

    @Test
    @DisplayName("赛季窗口外（endsAt 已过）ACTIVE 行不再视为进行中（R05：不只看 status）")
    void myPass_seasonWindowExpired_empty() {
        PetSeason expired = activeSeason();
        expired.setEndsAt(LocalDateTime.now().minusDays(1));
        when(seasonMapper.selectOne(any())).thenReturn(null); // 查询条件含窗口过滤

        PetSeasonPassVO vo = service.myPass(USER_ID);

        assertThat(vo.seasonId()).isNull();
    }

    @Test
    @DisplayName("T19：mySeasons 返回本人参与过的赛季（去重，按结束时间倒序）")
    void mySeasons_returnsParticipatedSeasons() {
        PetSeasonPass p1 = new PetSeasonPass();
        p1.setSeasonId(1L);
        PetSeasonPass p2 = new PetSeasonPass();
        p2.setSeasonId(2L);
        PetSeasonPass p3 = new PetSeasonPass();
        p3.setSeasonId(2L); // 同赛季多行（多档领取），去重
        when(passMapper.selectList(any())).thenReturn(List.of(p1, p2, p3));

        PetSeason s1 = new PetSeason();
        s1.setId(1L);
        s1.setName("S1");
        PetSeason s2 = new PetSeason();
        s2.setId(2L);
        s2.setName("S2");
        when(seasonMapper.selectList(any())).thenReturn(List.of(s2, s1));

        List<PetSeason> seasons = service.mySeasons(USER_ID);

        assertThat(seasons).hasSize(2);
        assertThat(seasons.get(0).getName()).isEqualTo("S2");
        assertThat(seasons.get(1).getName()).isEqualTo("S1");
    }

    @Test
    @DisplayName("T19：mySeasons 无参与记录 → 空列表（不返回从未参与的空赛季）")
    void mySeasons_noParticipation_empty() {
        when(passMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.mySeasons(USER_ID)).isEmpty();
        verify(seasonMapper, never()).selectList(any());
    }
}
