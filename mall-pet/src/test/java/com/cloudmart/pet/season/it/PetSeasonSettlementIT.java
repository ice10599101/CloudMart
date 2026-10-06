package com.cloudmart.pet.season.it;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonRanking;
import com.cloudmart.pet.entity.PetSeasonReward;
import com.cloudmart.pet.entity.PetSeasonSettlementJob;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRankingMapper;
import com.cloudmart.pet.repository.PetSeasonRewardMapper;
import com.cloudmart.pet.repository.PetSeasonSettlementJobMapper;
import com.cloudmart.pet.service.impl.PetSeasonSettlementTxWorker;
import com.cloudmart.pet.service.impl.PetStateService;
import com.cloudmart.pet.wallet.PetEconomyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * PET-02/T51/T52/T53：赛季发奖批事务边界（真实 MySQL + Spring 代理）。
 *
 * <p>修复前 TxWorker 为同类自调用（@Transactional 失效），钱包 MANDATORY 无事务必抛而
 * 奖励 CAS 已自动提交——"状态已写、入账必失败"的永久漏发只有真实代理才能证明。
 * 单测（mock）无法证明回滚语义，本 IT 用真实事务验证：</p>
 *
 * <ul>
 *   <li>正常批：奖励 CAS SUCCEEDED + 钱包入账（operationKey 含 seasonId+rankingId）同批提交；</li>
 *   <li>入账失败：整批回滚——奖励状态回 NULL，无半成功；</li>
 *   <li>重放：已 SUCCEEDED 行被 CAS 跳过，不重发（T53 重复执行只入账一次）；</li>
 *   <li>冻榜：FREEZING→SETTLING 原子推进 + 作业行创建；重入复用既有作业不重置游标（T51）。</li>
 * </ul>
 *
 * <p>economy/stateService/producer 为旁路（mock Bean）：钱包账实一致性由
 * PetWalletServiceIntegrationTest 单独覆盖，本 IT 聚焦赛季批的事务边界。</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PetSeasonSettlementIT.SeasonItConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.sentinel.enabled=false",
                "spring.flyway.clean-disabled=true"
        })
@ActiveProfiles("it")
@DisplayName("PET-02 赛季发奖批事务边界（真实 MySQL）")
class PetSeasonSettlementIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:9.4.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @MockitoBean
    private PetEconomyService economyService;
    @MockitoBean
    private PetStateService stateService;
    @MockitoBean
    private com.cloudmart.pet.mq.PetEventProducer eventProducer;

    @Configuration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    @MapperScan("com.cloudmart.pet.repository")
    @Import(PetSeasonSettlementTxWorker.class)
    static class SeasonItConfig {

        @Bean
        com.cloudmart.pet.config.PetProperties petProperties() {
            return new com.cloudmart.pet.config.PetProperties();
        }

        @Bean
        com.cloudmart.pet.config.PetClock petClock(com.cloudmart.pet.config.PetProperties properties) {
            return new com.cloudmart.pet.config.PetClock(java.time.Clock.systemUTC(), properties);
        }
    }

    @Autowired
    private PetSeasonSettlementTxWorker txWorker;
    @Autowired
    private PetSeasonMapper seasonMapper;
    @Autowired
    private PetSeasonRewardMapper rewardMapper;
    @Autowired
    private PetSeasonRankingMapper rankingMapper;
    @Autowired
    private PetSeasonSettlementJobMapper jobMapper;

    private static final AtomicLong SEQ = new AtomicLong(5000);

    private PetSeason newSeason(String status) {
        PetSeason season = new PetSeason();
        season.setId(SEQ.incrementAndGet());
        season.setName("IT-赛季" + season.getId());
        season.setStartsAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(7));
        season.setEndsAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        season.setStatus(status);
        seasonMapper.insert(season);
        return season;
    }

    private PetSeasonReward tier(PetSeason season, int min, int max) {
        PetSeasonReward reward = new PetSeasonReward();
        reward.setSeasonId(season.getId());
        reward.setRankMin(min);
        reward.setRankMax(max);
        reward.setRewardStarlight(100);
        reward.setRewardExp(0);
        rewardMapper.insert(reward);
        return reward;
    }

    private PetSeasonRanking ranking(PetSeason season, int rankNo) {
        PetSeasonRanking row = new PetSeasonRanking();
        row.setSeasonId(season.getId());
        row.setPetId(SEQ.incrementAndGet());
        row.setUserId(SEQ.incrementAndGet());
        row.setRankNo(rankNo);
        row.setLevel(5);
        row.setExp(50);
        rankingMapper.insert(row);
        return row;
    }

    @Test
    @DisplayName("T52：正常批——奖励 CAS + 钱包入账同批提交，operationKey 含 seasonId+rankingId")
    void settleBatchCommitsAtomically() {
        PetSeason season = newSeason("SETTLING");
        tier(season, 1, 10);
        PetSeasonRanking row = ranking(season, 1);
        when(economyService.earn(anyLong(), anyLong(), anyString(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(new PetEconomyService.WalletSettlement("COMPLETED", 100L, 300L, false, null));

        PetSeasonSettlementTxWorker.BatchOutcome outcome = txWorker.settleBatchInTx(
                season, rewardMapper.selectList(null), 0, 10, 500);

        assertThat(outcome).isNotNull();
        assertThat(outcome.rewarded()).isEqualTo(1);
        assertThat(rankingMapper.selectById(row.getId()).getRewardStatus()).isEqualTo("SUCCEEDED");
        // 幂等键：SEASON_REWARD:{seasonId}:{rankingId}（PET-02 规范）
        org.mockito.ArgumentCaptor<Object[]> keys = org.mockito.ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(economyService).earn(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq("SEASON_REWARD"),
                org.mockito.ArgumentMatchers.eq(season.getId()), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.isNull(), keys.capture());
        assertThat(keys.getValue()).containsExactly(season.getId(), row.getId());
    }

    @Test
    @DisplayName("T52：入账失败整批回滚——奖励状态回 NULL，无半成功")
    void settleBatchRollsBackOnFailure() {
        PetSeason season = newSeason("SETTLING");
        tier(season, 1, 10);
        PetSeasonRanking row = ranking(season, 1);
        when(economyService.earn(anyLong(), anyLong(), anyString(), anyLong(), anyLong(), any(), any(), any()))
                .thenThrow(new RuntimeException("钱包入账失败"));

        assertThatThrownBy(() -> txWorker.settleBatchInTx(season, rewardMapper.selectList(null), 0, 10, 500))
                .isInstanceOf(RuntimeException.class);

        // 真实事务回滚：CAS 标记一并撤销，可重跑
        assertThat(rankingMapper.selectById(row.getId()).getRewardStatus()).isNull();
    }

    @Test
    @DisplayName("T53：重放——已 SUCCEEDED 行被 CAS 跳过，不重发")
    void replaySkipsClaimedRows() {
        PetSeason season = newSeason("SETTLING");
        tier(season, 1, 10);
        ranking(season, 1);
        when(economyService.earn(anyLong(), anyLong(), anyString(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(new PetEconomyService.WalletSettlement("COMPLETED", 100L, 300L, false, null));
        txWorker.settleBatchInTx(season, rewardMapper.selectList(null), 0, 10, 500);
        Mockito.reset(economyService);

        PetSeasonSettlementTxWorker.BatchOutcome replay = txWorker.settleBatchInTx(
                season, rewardMapper.selectList(null), 0, 10, 500);

        assertThat(replay).isNotNull();
        assertThat(replay.rewarded()).isZero();
        Mockito.verify(economyService, Mockito.never())
                .earn(anyLong(), anyLong(), anyString(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("T51：冻榜原子推进 + 作业行创建；重入复用既有作业不重置游标")
    void freezeIsIdempotent() {
        PetSeason season = newSeason("FREEZING");
        ranking(season, 1);
        ranking(season, 2);

        txWorker.freezeInTx(season);

        PetSeason settled = seasonMapper.selectById(season.getId());
        assertThat(settled.getStatus()).isEqualTo("SETTLING");
        assertThat(settled.getSnapshotComplete()).isEqualTo(1);
        List<PetSeasonRanking> snapshot = rankingMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetSeasonRanking>()
                        .eq(PetSeasonRanking::getSeasonId, season.getId()));
        assertThat(snapshot).hasSize(2);
        PetSeasonSettlementJob job = jobMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetSeasonSettlementJob>()
                        .eq(PetSeasonSettlementJob::getSeasonId, season.getId()));
        assertThat(job).isNotNull();
        assertThat(job.getTotalCount()).isEqualTo(2);

        // 重入：复用既有作业（游标不重置），快照重建
        job.setCursorRank(1);
        jobMapper.updateById(job);
        txWorker.freezeInTx(season);
        assertThat(jobMapper.selectById(job.getId()).getCursorRank()).isEqualTo(1);
    }
}
