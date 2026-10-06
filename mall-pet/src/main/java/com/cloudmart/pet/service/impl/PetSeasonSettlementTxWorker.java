package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonRanking;
import com.cloudmart.pet.entity.PetSeasonReward;
import com.cloudmart.pet.entity.PetSeasonSettlementJob;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRankingMapper;
import com.cloudmart.pet.repository.PetSeasonSettlementJobMapper;
import com.cloudmart.pet.wallet.PetEconomyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 赛季结算事务工作器（PET-02）：冻榜与单批发奖的<strong>真实事务边界</strong>在这里。
 *
 * <p>必须独立于协调器 {@link PetSeasonSettlementService} 成 Bean：冻结/发奖原为同类自调用，
 * {@code @Transactional} 经 Spring 代理完全不生效——钱包 {@code credit/debit} 传播级别
 * {@code MANDATORY} 直接抛"无事务"，而奖励状态 CAS 自动提交，形成"状态已写、入账必失败"的
 * 永久漏发路径。工作器只做事务内原子写，租约/游标/调度由协调器负责。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetSeasonSettlementTxWorker {

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonRankingMapper rankingMapper;
    private final PetSeasonSettlementJobMapper jobMapper;
    private final com.cloudmart.pet.repository.PetMapper petMapper;
    private final PetEconomyService economyService;
    private final PetStateService stateService;
    private final PetEventProducer eventProducer;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 冻榜（FREEZING→SETTLING）：窗口函数 INSERT...SELECT 完整排名、作业行、
     * freezeAt/snapshotComplete/状态迁移同一事务提交。幂等可重入：FREEZING 期间没有任何
     * 已发奖励（发奖仅在 SETTLING），重入先清残留快照行再重算；CAS 失败说明并发执行者已
     * 推进状态，整事务回滚不覆盖他人快照。
     */
    @Transactional
    public void freezeInTx(PetSeason season) {
        rankingMapper.delete(new LambdaQueryWrapper<PetSeasonRanking>()
                .eq(PetSeasonRanking::getSeasonId, season.getId()));
        jdbcTemplate.update("""
                INSERT INTO pet_season_ranking
                    (season_id, pet_id, user_id, rank_no, level, exp, reward_status)
                SELECT ?, pet.id, pet.user_id,
                       ROW_NUMBER() OVER (ORDER BY pet.level DESC, pet.exp DESC, pet.id ASC),
                       pet.level, pet.exp, NULL
                FROM pet pet
                WHERE pet.is_public = 1 AND pet.id <> 0
                """, season.getId());
        int total = rankingMapper.selectCount(new LambdaQueryWrapper<PetSeasonRanking>()
                .eq(PetSeasonRanking::getSeasonId, season.getId())).intValue();
        PetSeasonSettlementJob job = new PetSeasonSettlementJob();
        job.setSeasonId(season.getId());
        job.setStatus("RUNNING");
        job.setCursorRank(0);
        job.setTotalCount(total);
        job.setSuccessCount(0);
        job.setFailureCount(0);
        // PET-26/T54：迟冻榜标记——快照按冻榜时刻的 level/exp 事实；若执行晚于结束超过
        // 1 小时（调度中断/feature 关闭后重入），期间升级会不可逆地混入排名，作业行留
        // LEGACY 说明供人工核对（正常路径每分钟驱动，冻结距 endsAt ≤1 分钟）。
        java.time.LocalDateTime freezeAt = LocalDateTime.now(ZoneOffset.UTC);
        boolean legacyFreeze = season.getEndsAt() != null
                && freezeAt.isAfter(season.getEndsAt().plusHours(1));
        String legacyNote = legacyFreeze
                ? "LEGACY: 冻榜晚于赛季结束超 1 小时，排名含结束后升级（按冻榜时刻事实，非截止时刻）"
                : null;
        if (legacyFreeze) {
            job.setLastError(legacyNote);
            log.warn("赛季迟冻榜（legacy 排名）: seasonId={}, endsAt={}, freezeAt={}",
                    season.getId(), season.getEndsAt(), freezeAt);
        }
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException e) {
            // 作业已存在（FREEZING 崩溃后重试重入）：复用既有作业，不重置游标
            if (legacyNote != null) {
                jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                        .set(PetSeasonSettlementJob::getLastError, legacyNote)
                        .eq(PetSeasonSettlementJob::getSeasonId, season.getId())
                        .eq(PetSeasonSettlementJob::getStatus, "RUNNING"));
            }
        }
        int advanced = seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                .set(PetSeason::getStatus, "SETTLING")
                .set(PetSeason::getFreezeAt, freezeAt)
                .set(PetSeason::getSnapshotComplete, 1)
                .eq(PetSeason::getId, season.getId())
                .eq(PetSeason::getStatus, "FREEZING"));
        if (advanced == 0) {
            // 并发执行者已推进状态机：本事务的快照写入整体作废，交由胜者继续
            throw new IllegalStateException("赛季已被并发执行者推进，冻榜回滚重试: seasonId=" + season.getId());
        }
        log.info("赛季冻榜完成: seasonId={}, name={}, total={}",
                season.getId(), season.getName(), total);
    }

    /**
     * 按快照名次游标发一批（本批一个事务：逐行奖励事实 CAS + 钱包 + 经验 + outbox 同事务提交）。
     * 游标无行推进返回 null（防御：快照行少于 totalCount 时不死循环）；
     * 奖励状态先标后付改为"同事务一并提交"——任意一步异常（含钱包 UNKNOWN）整批回滚，
     * 续跑从原游标重放，已 SUCCEEDED 行被 CAS 跳过不重发。
     */
    @Transactional
    public BatchOutcome settleBatchInTx(PetSeason season, List<PetSeasonReward> tiers,
                                        int cursorRank, int totalCount, int limit) {
        int batchEnd = Math.min(cursorRank + limit, Math.max(totalCount, cursorRank));
        List<PetSeasonRanking> rows = rankingMapper.selectList(new LambdaQueryWrapper<PetSeasonRanking>()
                .eq(PetSeasonRanking::getSeasonId, season.getId())
                .gt(PetSeasonRanking::getRankNo, cursorRank)
                .le(PetSeasonRanking::getRankNo, batchEnd)
                .orderByAsc(PetSeasonRanking::getRankNo));
        if (rows.isEmpty()) {
            return null;
        }
        int rewarded = 0;
        for (PetSeasonRanking row : rows) {
            // 一次性奖励事实（CAS NULL→SUCCEEDED）：并发 worker 只有一个胜者；重放零副作用
            int claimed = rankingMapper.update(null, new LambdaUpdateWrapper<PetSeasonRanking>()
                    .set(PetSeasonRanking::getRewardStatus, "SUCCEEDED")
                    .set(PetSeasonRanking::getRewardedAt, LocalDateTime.now(ZoneOffset.UTC))
                    .eq(PetSeasonRanking::getId, row.getId())
                    .isNull(PetSeasonRanking::getRewardStatus));
            if (claimed == 0) {
                continue;
            }
            PetSeasonReward tier = tierOf(tiers, row.getRankNo());
            if (tier != null) {
                long starlight = tier.getRewardStarlight() != null ? tier.getRewardStarlight() : 0;
                int expReward = tier.getRewardExp() != null ? tier.getRewardExp() : 0;
                if (starlight > 0) {
                    // 幂等入账：operationKey 按 PET-02 规范含 seasonId+rankingId（快照行唯一即奖励唯一），
                    // 同 key 重跑不重发；UNKNOWN 抛出回滚本批（奖励事实随之回滚，续跑重新发）
                    PetEconomyService.WalletSettlement settlement = economyService.earn(
                            row.getUserId(), row.getPetId(),
                            "SEASON_REWARD", season.getId(), starlight, null,
                            season.getId(), row.getId());
                    if (settlement.isUnknown()) {
                        throw economyService.settlementPending();
                    }
                }
                if (expReward > 0) {
                    stateService.grantExp(requirePet(row.getPetId()), expReward);
                }
                eventProducer.publishViaOutbox(RocketMQConfig.PET_TAG_PROACTIVE,
                        new PetEventProducer.PetEventMessage(
                                "SEASON_REWARD:" + season.getId() + ":" + row.getPetId(),
                                String.valueOf(row.getUserId()), "PET_SEASON_REWARD",
                                "赛季结算奖励到账啦！",
                                "主人！「" + season.getName() + "」赛季我拿到了第 " + row.getRankNo()
                                        + " 名，奖励已发放，快去看看吧！",
                                String.valueOf(season.getId()), "PET_SEASON_REWARD"),
                        row.getPetId());
                rewarded++;
            }
        }
        return new BatchOutcome(rows.get(rows.size() - 1).getRankNo(), rewarded);
    }

    /** 批结果：处理到的名次（checkpoint 依据）+ 实际发奖人数 */
    public record BatchOutcome(int processedToRank, int rewarded) {
    }

    /** 发经验需完整实体（grantExp 依赖 version CAS 与亲密度修正），不部分映射 */
    private com.cloudmart.pet.entity.Pet requirePet(Long petId) {
        com.cloudmart.pet.entity.Pet pet = petMapper.selectById(petId);
        if (pet == null) {
            throw new IllegalStateException("快照宠物不存在: " + petId);
        }
        return pet;
    }

    /** 命中奖励梯度（rank_min ≤ rank ≤ rank_max；区间互斥由 uk(rank_min) 与录入校验保证） */
    private PetSeasonReward tierOf(List<PetSeasonReward> tiers, int rank) {
        return tiers.stream()
                .filter(t -> rank >= t.getRankMin() && rank <= t.getRankMax())
                .findFirst()
                .orElse(null);
    }
}
