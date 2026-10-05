package com.cloudmart.pet.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonRanking;
import com.cloudmart.pet.entity.PetSeasonReward;
import com.cloudmart.pet.entity.PetSeasonSettlementJob;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRewardMapper;
import com.cloudmart.pet.repository.PetSeasonSettlementJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PET-02 赛季结算协调器测试：租约 fence（旧执行者失去租约立即停止）、SETTLING 无作业回退
 * FREEZING 重冻榜、梯度替换锁内重读状态（TOCTOU 修复）、批次失败游标不推进、梯度校验
 * （R06/R17 原有用例保留）。
 */
@DisplayName("PetSeasonSettlementService 协调器测试（PET-02/R06/R17）")
class PetSeasonSettlementServiceTest {

    private PetSeasonMapper seasonMapper;
    private PetSeasonRewardMapper rewardMapper;
    private PetSeasonSettlementJobMapper jobMapper;
    private PetSeasonSettlementTxWorker txWorker;
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private PetSeasonSettlementService service;

    @BeforeEach
    void setUp() {
        // MyBatis-Plus Lambda wrapper 依赖实体元数据缓存，纯单测需手动初始化（项目既有模式）
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PetSeason.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PetSeasonSettlementJob.class);
        seasonMapper = mock(PetSeasonMapper.class);
        rewardMapper = mock(PetSeasonRewardMapper.class);
        jobMapper = mock(PetSeasonSettlementJobMapper.class);
        txWorker = mock(PetSeasonSettlementTxWorker.class);
        jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        service = new PetSeasonSettlementService(
                seasonMapper, rewardMapper, jobMapper,
                new com.cloudmart.pet.config.PetProperties(), txWorker, jdbcTemplate);
    }

    @Test
    @DisplayName("合法梯度：从第 1 名连续覆盖，通过")
    void validTiers() {
        assertThatCode(() -> service.validateTiers(List.of(
                tier(1, 10, 100, 50), tier(11, 50, 50, 20), tier(51, 200, 10, 5))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R17：rankMin 为 null 在排序前被拒绝（不再 NPE 500）")
    void nullRankMinRejected() {
        PetSeasonReward bad = new PetSeasonReward();
        bad.setRankMax(10);
        assertThatThrownBy(() -> service.validateTiers(List.of(bad)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("负奖励拒绝（R17：原实现未限制）")
    void negativeRewardRejected() {
        assertThatThrownBy(() -> service.validateTiers(List.of(tier(1, 10, -5, 50))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("间隙/重叠拒绝：梯度必须从第 1 名连续覆盖")
    void gapAndOverlapRejected() {
        assertThatThrownBy(() -> service.validateTiers(List.of(tier(2, 10, 10, 0))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
        assertThatThrownBy(() -> service.validateTiers(List.of(tier(1, 10, 10, 0), tier(10, 20, 5, 0))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("PET-02：梯度替换锁内重读赛季状态，FREEZING 一律拒绝（TOCTOU 修复）")
    void replaceTiersRejectsFrozenSeasonByRereadingStatus() {
        PetSeason frozen = new PetSeason();
        frozen.setId(7L);
        frozen.setStatus("FREEZING");
        when(jdbcTemplate.queryForMap("SELECT id FROM pet_season WHERE id = ? FOR UPDATE", 7L))
                .thenReturn(Map.of("id", 7L));
        when(seasonMapper.selectById(7L)).thenReturn(frozen);
        assertThatThrownBy(() -> service.replaceTiersGuarded(7L, List.of(tier(1, 10, 10, 0))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
        verify(rewardMapper, never()).delete(any());
    }

    @Test
    @DisplayName("PET-02：SETTLING 无作业行回退 FREEZING，下轮重冻榜而非空转")
    void settlingWithoutJobFallsBackToFreezing() {
        PetSeason settling = season("SETTLING");
        when(seasonMapper.selectList(any())).thenReturn(List.of(settling));
        when(seasonMapper.selectById(5L)).thenReturn(settling);
        when(jobMapper.selectOne(any())).thenReturn(null);

        service.settleExpiredSeasons();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetSeason>> fallback =
                (ArgumentCaptor) ArgumentCaptor.forClass(
                        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(seasonMapper).update(any(), fallback.capture());
        assertThat(fallback.getValue().getParamNameValuePairs().values()).contains("FREEZING");
        verify(txWorker, never()).settleBatchInTx(any(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("PET-02：游标推进 fence 命中 0 行（租约被接管）→ 停止驱动且不 finalize")
    void cursorAdvanceWithLostLeaseStopsDriver() {
        PetSeason settling = season("SETTLING");
        when(seasonMapper.selectList(any())).thenReturn(List.of(settling));
        PetSeasonSettlementJob job = job(0, 2);
        when(seasonMapper.selectById(5L)).thenReturn(settling);
        when(jobMapper.selectOne(any())).thenReturn(job);
        // 第一次 update = 抢租约成功；第二次 = 游标推进 fence 不命中（新执行者已递增 lease_version）
        when(jobMapper.update(any(), any())).thenReturn(1).thenReturn(0);
        when(jobMapper.selectById(job.getId())).thenReturn(jobWith(job, 1L));
        when(rewardMapper.selectList(any())).thenReturn(List.of(tier(1, 10, 100, 0)));
        when(txWorker.settleBatchInTx(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new PetSeasonSettlementTxWorker.BatchOutcome(1, 1));

        service.settleExpiredSeasons();

        // 旧执行者只尝试一批即停止；未把赛季标成 SETTLED
        verify(txWorker, times(1)).settleBatchInTx(any(), any(), anyInt(), anyInt(), anyInt());
        verify(seasonMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("PET-02：批次事务异常不推进游标，等待下轮续跑")
    void batchFailureKeepsCursorForRetry() {
        PetSeason settling = season("SETTLING");
        when(seasonMapper.selectList(any())).thenReturn(List.of(settling));
        PetSeasonSettlementJob job = job(0, 2);
        when(seasonMapper.selectById(5L)).thenReturn(settling);
        when(jobMapper.selectOne(any())).thenReturn(job);
        when(jobMapper.update(any(), any())).thenReturn(1);
        when(jobMapper.selectById(job.getId())).thenReturn(jobWith(job, 1L));
        when(rewardMapper.selectList(any())).thenReturn(List.of(tier(1, 10, 100, 0)));
        when(txWorker.settleBatchInTx(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("钱包入账失败"));

        service.settleExpiredSeasons();

        ArgumentCaptor<Integer> cursor = ArgumentCaptor.forClass(Integer.class);
        verify(txWorker).settleBatchInTx(any(), any(), cursor.capture(), anyInt(), anyInt());
        assertThat(cursor.getValue()).isZero();
    }

    @Test
    @DisplayName("PET-02：抢不到租约不进入发奖批次")
    void leaseNotClaimedSkipsBatch() {
        PetSeason settling = season("SETTLING");
        when(seasonMapper.selectList(any())).thenReturn(List.of(settling));
        when(seasonMapper.selectById(5L)).thenReturn(settling);
        when(jobMapper.selectOne(any())).thenReturn(job(0, 2));
        when(jobMapper.update(any(), any())).thenReturn(0);

        service.settleExpiredSeasons();

        verify(txWorker, never()).settleBatchInTx(any(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("PET-02：FREEZING 赛季驱动幂等重冻榜（不再滞留找作业的空循环）")
    void freezingSeasonRefreezes() {
        PetSeason freezing = season("FREEZING");
        when(seasonMapper.selectList(any())).thenReturn(List.of(freezing));
        when(seasonMapper.selectById(5L)).thenReturn(freezing);

        service.settleExpiredSeasons();

        verify(txWorker).freezeInTx(freezing);
    }

    @Test
    @DisplayName("PET-02：管理触发只建作业——请求线程不直接发奖（批次交给调度器续跑）")
    void requestSettlementOnlyDrivesStateMachine() {
        PetSeason expired = season("ACTIVE");
        when(seasonMapper.selectById(5L)).thenReturn(expired, season("SETTLING"));
        // ACTIVE→FREEZING CAS 成功
        when(seasonMapper.update(any(), any())).thenReturn(1);

        service.requestSettlement(5L);

        verify(txWorker).freezeInTx(any(PetSeason.class));
        verify(txWorker, never()).settleBatchInTx(any(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("管理触发：未到期赛季拒绝")
    void requestSettlementRejectsNotExpired() {
        PetSeason active = season("ACTIVE");
        active.setEndsAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(seasonMapper.selectById(5L)).thenReturn(active);
        assertThatThrownBy(() -> service.requestSettlement(5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("PET-02：重试仅对有失败记录的 RUNNING 作业开放")
    void retryOnlyForFailedRunningJob() {
        PetSeason settling = season("SETTLING");
        when(seasonMapper.selectById(5L)).thenReturn(settling);
        when(jobMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.retrySettlement(5L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_STATE_CONFLICT);
        verify(txWorker, never()).settleBatchInTx(any(), any(), anyInt(), anyInt(), anyInt());
    }

    private PetSeason season(String status) {
        PetSeason season = new PetSeason();
        season.setId(5L);
        season.setName("S1");
        season.setStatus(status);
        season.setEndsAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        return season;
    }

    private PetSeasonSettlementJob job(int cursor, int total) {
        PetSeasonSettlementJob job = new PetSeasonSettlementJob();
        job.setId(9L);
        job.setSeasonId(5L);
        job.setStatus("RUNNING");
        job.setCursorRank(cursor);
        job.setTotalCount(total);
        job.setSuccessCount(0);
        return job;
    }

    private PetSeasonSettlementJob jobWith(PetSeasonSettlementJob job, long leaseVersion) {
        PetSeasonSettlementJob copy = new PetSeasonSettlementJob();
        copy.setId(job.getId());
        copy.setSeasonId(job.getSeasonId());
        copy.setStatus(job.getStatus());
        copy.setCursorRank(job.getCursorRank());
        copy.setTotalCount(job.getTotalCount());
        copy.setSuccessCount(job.getSuccessCount());
        copy.setLeaseVersion(leaseVersion);
        return copy;
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
