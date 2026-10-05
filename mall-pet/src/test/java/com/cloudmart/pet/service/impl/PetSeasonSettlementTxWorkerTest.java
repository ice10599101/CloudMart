package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonRanking;
import com.cloudmart.pet.entity.PetSeasonReward;
import com.cloudmart.pet.entity.PetSeasonSettlementJob;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRankingMapper;
import com.cloudmart.pet.repository.PetSeasonSettlementJobMapper;
import com.cloudmart.pet.wallet.PetEconomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PET-02 赛季发奖事务工作器测试：奖励事实 CAS + 钱包/经验/outbox 同批原子语义（单测覆盖
 * 调用编排；事务原子性由真实 Spring+MySQL 集成测试按方案 T52 兜底验证）。
 */
@DisplayName("PetSeasonSettlementTxWorker 发奖批测试（PET-02）")
class PetSeasonSettlementTxWorkerTest {

    private PetSeasonMapper seasonMapper;
    private PetSeasonRankingMapper rankingMapper;
    private PetSeasonSettlementJobMapper jobMapper;
    private PetMapper petMapper;
    private PetEconomyService economyService;
    private PetStateService stateService;
    private PetSeasonSettlementTxWorker worker;
    private PetSeason season;

    @BeforeEach
    void setUp() {
        // MyBatis-Plus Lambda wrapper 依赖实体元数据缓存，纯单测需手动初始化（项目既有模式）
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PetSeason.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PetSeasonRanking.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PetSeasonSettlementJob.class);
        seasonMapper = mock(PetSeasonMapper.class);
        rankingMapper = mock(PetSeasonRankingMapper.class);
        jobMapper = mock(PetSeasonSettlementJobMapper.class);
        petMapper = mock(PetMapper.class);
        economyService = mock(PetEconomyService.class);
        stateService = mock(PetStateService.class);
        worker = new PetSeasonSettlementTxWorker(seasonMapper, rankingMapper, jobMapper, petMapper,
                economyService, stateService,
                mock(com.cloudmart.pet.mq.PetEventProducer.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
        season = new PetSeason();
        season.setId(5L);
        season.setName("S1");
    }

    @Test
    @DisplayName("发奖批：CAS 胜出行 → 钱包入账（operationKey 含 seasonId+rankingId）→ 计入批次")
    void batchRewardsClaimedRowsWithFencedEconomyKey() {
        PetSeasonRanking row = rankingRow(1);
        when(rankingMapper.selectList(any())).thenReturn(List.of(row));
        when(rankingMapper.update(any(), any())).thenReturn(1);
        when(economyService.earn(anyLong(), anyLong(), any(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(new PetEconomyService.WalletSettlement("COMPLETED", 100L, 300L, false, null));

        PetSeasonSettlementTxWorker.BatchOutcome outcome = worker.settleBatchInTx(
                season, List.of(tier(1, 10, 100, 0)), 0, 10, 500);

        assertThat(outcome.processedToRank()).isEqualTo(1);
        assertThat(outcome.rewarded()).isEqualTo(1);
        ArgumentCaptor<Object[]> keyParts = ArgumentCaptor.forClass(Object[].class);
        verify(economyService).earn(eq(22L), eq(11L), eq("SEASON_REWARD"), eq(5L),
                eq(100L), eq(null), keyParts.capture());
        assertThat(keyParts.getValue()).containsExactly(5L, row.getId());
    }

    @Test
    @DisplayName("发奖批：已被并发执行者 CAS 占用的行跳过，不重复入账")
    void batchSkipsAlreadyClaimedRows() {
        when(rankingMapper.selectList(any())).thenReturn(List.of(rankingRow(1)));
        when(rankingMapper.update(any(), any())).thenReturn(0);

        PetSeasonSettlementTxWorker.BatchOutcome outcome = worker.settleBatchInTx(
                season, List.of(tier(1, 10, 100, 0)), 0, 10, 500);

        assertThat(outcome.processedToRank()).isEqualTo(1);
        assertThat(outcome.rewarded()).isZero();
        verify(economyService, never()).earn(anyLong(), anyLong(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("发奖批：钱包 UNKNOWN 抛出（真实事务下整批回滚，含奖励状态 CAS）")
    void batchThrowsOnUnknownSettlement() {
        when(rankingMapper.selectList(any())).thenReturn(List.of(rankingRow(1)));
        when(rankingMapper.update(any(), any())).thenReturn(1);
        when(economyService.earn(anyLong(), anyLong(), any(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(new PetEconomyService.WalletSettlement("UNKNOWN", 0L, null, false, "timeout"));
        when(economyService.settlementPending()).thenReturn(new BusinessException(
                PetErrorCodes.PET_SETTLEMENT_PENDING, "结算处理中"));

        assertThatThrownBy(() -> worker.settleBatchInTx(season, List.of(tier(1, 10, 100, 0)), 0, 10, 500))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_SETTLEMENT_PENDING);
    }

    @Test
    @DisplayName("发奖批：经验奖励走状态服务（完整实体），星光为 0 不入账")
    void batchGrantsExpThroughStateService() {
        Pet pet = new Pet();
        pet.setId(11L);
        when(rankingMapper.selectList(any())).thenReturn(List.of(rankingRow(1)));
        when(rankingMapper.update(any(), any())).thenReturn(1);
        when(petMapper.selectById(11L)).thenReturn(pet);

        PetSeasonSettlementTxWorker.BatchOutcome outcome = worker.settleBatchInTx(
                season, List.of(tier(1, 10, 0, 50)), 0, 10, 500);

        assertThat(outcome.rewarded()).isEqualTo(1);
        verify(economyService, never()).earn(anyLong(), anyLong(), any(), anyLong(), anyLong(), any(), any());
        verify(stateService).grantExp(pet, 50);
    }

    @Test
    @DisplayName("发奖批：游标区间无快照行返回 null（防死循环，人工核查差异）")
    void batchReturnsNullWhenNoRows() {
        when(rankingMapper.selectList(any())).thenReturn(List.of());
        assertThat(worker.settleBatchInTx(season, List.of(tier(1, 10, 100, 0)), 0, 10, 500)).isNull();
    }

    @Test
    @DisplayName("冻榜：CAS 状态迁移失败（并发执行者已推进）→ 抛出回滚本事务")
    void freezeRollsBackWhenSeasonCasFails() {
        when(rankingMapper.selectCount(any())).thenReturn(3L);
        when(seasonMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> worker.freezeInTx(season))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("并发执行者推进");
    }

    private PetSeasonRanking rankingRow(int rank) {
        PetSeasonRanking row = new PetSeasonRanking();
        row.setId(100L + rank);
        row.setSeasonId(5L);
        row.setPetId(11L);
        row.setUserId(22L);
        row.setRankNo(rank);
        row.setLevel(5);
        row.setExp(50);
        return row;
    }

    private PetSeasonReward tier(int min, int max, int starlight, int exp) {
        PetSeasonReward reward = new PetSeasonReward();
        reward.setRankMin(min);
        reward.setRankMax(max);
        reward.setRewardStarlight(starlight);
        reward.setRewardExp(exp);
        return reward;
    }
}
