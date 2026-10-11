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
import com.cloudmart.pet.repository.PetSeasonPassExpFactMapper;
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
    private PetSeasonPassExpFactMapper expFactMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetRewardApplicationService rewardApplicationService;
    @Mock
    private PetService petService;

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
                expFactMapper, inventoryMapper, rewardApplicationService, petService);
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
            when(claimMapper.selectList(any())).thenReturn(List.of(claim(1, "CLAIM_PENDING")));
            when(passMapper.selectOne(any())).thenReturn(pass(60));
            when(passMapper.update(any(), any())).thenReturn(1);

            int settled = service.settlePendingClaims();

            assertThat(settled).isEqualTo(1);
            verify(rewardApplicationService).claim(any(), any());
            ArgumentCaptor<PetSeasonPassClaim> captor = ArgumentCaptor.forClass(PetSeasonPassClaim.class);
            verify(claimMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("SETTLED");
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
    @DisplayName("经验事实去重")
    class ExpFactTests {

        @Test
        @DisplayName("同一任务事实重复上报 → 只计一次（uk 去重，pass_exp 只加一次）")
        void addExp_sameFactCountedOnce() {
            when(seasonMapper.selectOne(any())).thenReturn(activeSeason());
            when(passMapper.selectOne(any())).thenReturn(pass(10));
            when(expFactMapper.insert(any(PetSeasonPassExpFact.class)))
                    .thenReturn(1)
                    .thenThrow(new DuplicateKeyException("uk_fact"));

            service.addExp(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-100");
            service.addExp(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-100");

            // 第二次命中去重不再累加
            verify(passMapper, times(1)).update(any(), any());
        }

        @Test
        @DisplayName("缺来源事实 ID → 编程错误直接抛出（不留无事实经验）")
        void addExp_missingFactId_rejected() {
            assertThatThrownBy(() -> service.addExp(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("无进行中赛季 → 忽略（不记事实不累积）")
        void addExp_noActiveSeason_ignored() {
            when(seasonMapper.selectOne(any())).thenReturn(null);

            service.addExp(USER_ID, 5, PetSeasonPassExpFact.SOURCE_QUEST, "q-1");

            verify(expFactMapper, never()).insert(any(PetSeasonPassExpFact.class));
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
}
