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
import com.cloudmart.pet.repository.PetSeasonRewardMapper;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 赛季结算服务（F2/R06）：冻榜与发奖分离，SETTLED 只在快照完整且全部名次发奖完成后写入。
 *
 * <p>状态机：ACTIVE →(到期 CAS)→ FREEZING →(一次一致性快照同事务)→ SETTLING →(游标推进完)→ SETTLED。
 * 失败不回退 ACTIVE：错误记入作业行，租约到期由同键重试/恢复扫描接管——修复原实现
 * "先标 SETTLED 再结算 + kill 后 catch 回退不执行 = 赛季永久漏结算"。</p>
 *
 * <p>排名一次成型：FREEZING 中用窗口函数 INSERT...SELECT 冻结 {@code level DESC, exp DESC, petId ASC}
 * 完整快照（不再对实时 pet 表分页——批间经验变化使排名漂移）；发奖按快照 rank 游标逐批，
 * 每宠奖励事实 {@code reward_status NULL→SUCCEEDED} CAS 一次性入账，钱包 operationKey
 * SEASON_REWARD:{seasonId}:{petId} 双保险幂等（重复结算不重发）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetSeasonSettlementService {

    /** 发奖批次大小（每批一个事务） */
    private static final int SETTLE_BATCH = 500;
    /** 执行者租约时长：正常批次毫秒级，超时残留即视为执行者已丢失 */
    private static final long LEASE_SECONDS = 300;

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonRewardMapper rewardMapper;
    private final PetSeasonRankingMapper rankingMapper;
    private final PetSeasonSettlementJobMapper jobMapper;
    private final com.cloudmart.pet.repository.PetMapper petMapper;
    /** §13.1：seasonSettlementV2 关闭时定时结算不推进（可手动重入，赛季状态保留） */
    private final com.cloudmart.pet.config.PetProperties properties;
    private final PetEconomyService economyService;
    private final PetEventProducer eventProducer;
    private final PetStateService stateService;
    private final JdbcTemplate jdbcTemplate;
    private final String leaseOwner = UUID.randomUUID().toString().substring(0, 8)
            + ":" + Thread.currentThread().threadId();

    /** 每小时检查到期赛季（scheduler 调用）：CAS 占 FREEZING → 冻榜 → 驱动发奖 */
    public void settleExpiredSeasons() {
        if (!properties.getFeatureSwitches().isSeasonSettlementV2()) {
            return;
        }
        List<PetSeason> expired = seasonMapper.selectList(new LambdaQueryWrapper<PetSeason>()
                .in(PetSeason::getStatus, "ACTIVE", "FREEZING", "SETTLING")
                .le(PetSeason::getEndsAt, LocalDateTime.now(ZoneOffset.UTC))
                .last("LIMIT 10"));
        for (PetSeason season : expired) {
            try {
                driveSeason(season);
            } catch (Exception e) {
                // 单赛季失败不阻断其余赛季；错误已落作业行，下轮租约到期接管
                log.error("赛季结算驱动失败: seasonId={}", season.getId(), e);
            }
        }
    }

    /**
     * R06 赛季创建守卫：pet_season_guard 单例行 FOR UPDATE 串行化 + 行锁内复验
     * "仅一个进行中赛季"——两管理员并发创建不再依赖先 count 后 insert 的读快照。
     */
    @Transactional
    public void createSeasonGuarded(PetSeason season) {
        jdbcTemplate.queryForMap("SELECT id FROM pet_season_guard WHERE id = 1 FOR UPDATE");
        long activeCount = seasonMapper.selectCount(new LambdaQueryWrapper<PetSeason>()
                .in(PetSeason::getStatus, "ACTIVE", "FREEZING", "SETTLING")
                .gt(PetSeason::getEndsAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (activeCount > 0) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                    "已有进行中的赛季，先等它结算或结束");
        }
        try {
            seasonMapper.insert(season);
        } catch (DuplicateKeyException e) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_STATE_CONFLICT, "赛季创建冲突，请刷新重试");
        }
    }

    /** 管理端手动触发（R06）：只负责把到期 ACTIVE 赛季推进状态机，异步由作业驱动完成 */
    public void requestSettlement(Long seasonId) {
        PetSeason season = seasonMapper.selectById(seasonId);
        if (season == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "赛季不存在");
        }
        if (!"ACTIVE".equals(season.getStatus())) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                    "赛季不在进行中（当前: " + season.getStatus() + "）");
        }
        if (season.getEndsAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "赛季尚未到期");
        }
        driveSeason(season);
    }

    /** 状态机驱动：ACTIVE→冻榜；FREEZING/SETTLING→续跑（外部中断无感知恢复） */
    private void driveSeason(PetSeason season) {
        switch (season.getStatus()) {
            case "ACTIVE" -> {
                int claimed = seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                        .set(PetSeason::getStatus, "FREEZING")
                        .eq(PetSeason::getId, season.getId())
                        .eq(PetSeason::getStatus, "ACTIVE"));
                if (claimed > 0) {
                    freeze(seasonMapper.selectById(season.getId()));
                }
                // 冻榜失败（快照未完整）停留 FREEZING，下轮重试；错误已记录
            }
            case "FREEZING", "SETTLING" -> settleFromJob(seasonMapper.selectById(season.getId()));
            default -> {
                // SETTLED：无需处理
            }
        }
    }

    /**
     * 冻榜（FREEZING→SETTLING）：一次一致性快照——窗口函数 INSERT...SELECT 完整排名、
     * 作业行、season.freezeAt/snapshotComplete/状态迁移同一事务；失败整事务回滚留 FREEZING 重试。
     */
    @Transactional
    public void freeze(PetSeason season) {
        try {
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
            try {
                jobMapper.insert(job);
            } catch (DuplicateKeyException e) {
                // 作业已存在（FREEZING 崩溃后重试重入）：复用既有作业，不重置游标
            }
            seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                    .set(PetSeason::getStatus, "SETTLING")
                    .set(PetSeason::getFreezeAt, LocalDateTime.now(ZoneOffset.UTC))
                    .set(PetSeason::getSnapshotComplete, 1)
                    .eq(PetSeason::getId, season.getId())
                    .eq(PetSeason::getStatus, "FREEZING"));
            log.info("赛季冻榜完成: seasonId={}, name={}, total={}",
                    season.getId(), season.getName(), total);
        } catch (Exception e) {
            // 冻榜失败：状态保持 FREEZING（catch 不可吞掉状态机推进条件），下轮重试
            log.error("赛季冻榜失败（保持 FREEZING 待重试）: seasonId={}", season.getId(), e);
            throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
        }
    }

    /** SETTLING 驱动：抢/续作业租约 → 按快照游标分批发奖 → 游标到底 CAS SETTLED */
    private void settleFromJob(PetSeason season) {
        PetSeasonSettlementJob job = jobMapper.selectOne(new LambdaQueryWrapper<PetSeasonSettlementJob>()
                .eq(PetSeasonSettlementJob::getSeasonId, season.getId()));
        if (job == null) {
            // SETTLING 但无作业行（异常态）：标记回 FREEZING 重冻榜
            seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                    .set(PetSeason::getStatus, "FREEZING")
                    .set(PetSeason::getSnapshotComplete, 0)
                    .eq(PetSeason::getId, season.getId())
                    .eq(PetSeason::getStatus, "SETTLING"));
            log.warn("赛季 SETTLING 无作业行，已回退 FREEZING 重冻榜: seasonId={}", season.getId());
            return;
        }
        if ("COMPLETED".equals(job.getStatus())) {
            finalizeSeason(season, job);
            return;
        }
        if (!tryClaimLease(job)) {
            return;
        }
        List<PetSeasonReward> tiers = rewardMapper.selectList(new LambdaQueryWrapper<PetSeasonReward>()
                .eq(PetSeasonReward::getSeasonId, season.getId())
                .orderByAsc(PetSeasonReward::getRankMin));
        int success = 0;
        int failure = 0;
        try {
            while (job.getCursorRank() < job.getTotalCount()) {
                BatchOutcome outcome = settleBatchFromSnapshot(season, tiers, job.getCursorRank(), SETTLE_BATCH);
                if (outcome == null) {
                    // 防御：快照行少于 totalCount（不应发生）——停止推进避免死循环，人工核查
                    jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                            .set(PetSeasonSettlementJob::getLastError, "快照行数少于 totalCount，游标无行可推进")
                            .set(PetSeasonSettlementJob::getNextRetryAt, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(300))
                            .eq(PetSeasonSettlementJob::getId, job.getId()));
                    break;
                }
                advanceCursor(job, outcome.processedToRank(), outcome.rewarded());
                success += outcome.rewarded();
                job.setCursorRank(outcome.processedToRank());
                job.setSuccessCount(job.getSuccessCount() + outcome.rewarded());
            }
        } catch (Exception e) {
            failure++;
            jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                    .set(PetSeasonSettlementJob::getLastError, truncate(String.valueOf(e.getMessage())))
                    .set(PetSeasonSettlementJob::getNextRetryAt, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(60))
                    .eq(PetSeasonSettlementJob::getId, job.getId()));
            log.error("赛季发奖批次失败（游标已持久化，租约到期续跑）: seasonId={}, cursor={}",
                    season.getId(), job.getCursorRank(), e);
        }
        job = jobMapper.selectById(job.getId());
        if (job.getCursorRank() >= job.getTotalCount()) {
            finalizeSeason(season, job);
        }
    }

    /** CAS 抢/续租约（仅当到期或空闲）；胜者续期 300 秒 */
    private boolean tryClaimLease(PetSeasonSettlementJob job) {
        int claimed = jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getLeaseOwner, leaseOwner)
                .set(PetSeasonSettlementJob::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS))
                .setSql("lease_version = lease_version + 1")
                .eq(PetSeasonSettlementJob::getId, job.getId())
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING")
                .and(w -> w.isNull(PetSeasonSettlementJob::getLeaseUntil)
                        .or().le(PetSeasonSettlementJob::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC))));
        return claimed == 1;
    }

    /** 批结果：处理到的名次（checkpoint 依据）+ 实际发奖人数 */
    public record BatchOutcome(int processedToRank, int rewarded) {
    }

    /**
     * 按快照名次游标发一批（本批一个事务：逐行奖励事实 CAS + 钱包 + 经验 + 通知）。
     * 游标无行推进返回 null（防御：快照行少于 totalCount 时不死循环）；
     * 事务提交后由驱动方推进作业游标（checkpoint 只在事实可验证后推进）。
     */
    @Transactional
    public BatchOutcome settleBatchFromSnapshot(PetSeason season, List<PetSeasonReward> tiers, int cursorRank, int limit) {
        int batchEnd = Math.min(cursorRank + limit, seasonMaxRank(season.getId()));
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
                    // 幂等入账：operationKey=SEASON_REWARD:{seasonId}:{petId}，同 key 重跑不重发；
                    // UNKNOWN 抛 503 回滚本批（奖励事实随之回滚，续跑重新发）
                    PetEconomyService.WalletSettlement settlement = economyService.earn(
                            row.getUserId(), row.getPetId(),
                            "SEASON_REWARD", season.getId(), starlight, null,
                            season.getId(), row.getPetId());
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

    /** 批事务提交后由驱动方推进游标（checkpoint 只在事实提交后前进） */
    void advanceCursor(PetSeasonSettlementJob job, int processedToRank, int rewarded) {
        jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getCursorRank, processedToRank)
                .setSql("success_count = success_count + " + Math.max(rewarded, 0))
                .set(PetSeasonSettlementJob::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS))
                .eq(PetSeasonSettlementJob::getId, job.getId())
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING"));
    }

    /** 游标到底：CAS SETTLING→SETTLED（仅 snapshotComplete=1 且作业 COMPLETED 时可达） */
    private void finalizeSeason(PetSeason season, PetSeasonSettlementJob job) {
        jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getStatus, "COMPLETED")
                .eq(PetSeasonSettlementJob::getId, job.getId())
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING"));
        int settled = seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                .set(PetSeason::getStatus, "SETTLED")
                .set(PetSeason::getSettledAt, LocalDateTime.now(ZoneOffset.UTC))
                .eq(PetSeason::getId, season.getId())
                .eq(PetSeason::getStatus, "SETTLING")
                .eq(PetSeason::getSnapshotComplete, 1));
        if (settled > 0) {
            log.info("赛季结算完成: seasonId={}, name={}, total={}, success={}",
                    season.getId(), season.getName(), job.getTotalCount(), job.getSuccessCount());
        }
    }

    private int seasonMaxRank(Long seasonId) {
        PetSeasonSettlementJob job = jobMapper.selectOne(new LambdaQueryWrapper<PetSeasonSettlementJob>()
                .eq(PetSeasonSettlementJob::getSeasonId, seasonId));
        return job != null ? job.getTotalCount() : 0;
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

    /**
     * R17 奖励梯度原子替换：锁住赛季行再 delete+insert 同事务——中途失败整体回滚，
     * 原梯度完整保留；FREEZING/SETTLING/SETTLED 一律禁止修改（进行中结算的奖励口径不可变）。
     */
    @Transactional
    public void replaceTiersGuarded(Long seasonId, String seasonStatus, List<PetSeasonReward> tiers) {
        if (!"ACTIVE".equals(seasonStatus)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                    "当前赛季状态（" + seasonStatus + "）不可修改奖励梯度");
        }
        jdbcTemplate.queryForMap("SELECT id FROM pet_season WHERE id = ? FOR UPDATE", seasonId);
        rewardMapper.delete(new LambdaQueryWrapper<PetSeasonReward>()
                .eq(PetSeasonReward::getSeasonId, seasonId));
        tiers.forEach(rewardMapper::insert);
    }

    /**
     * 校验奖励梯度（管理端保存前调用）：null 先拒（R17：原实现 sorted 时 NPE 前置）、
     * 区间合法且互斥、按 rank_min 升序、禁止负奖励。
     */
    public void validateTiers(List<PetSeasonReward> tiers) {
        if (tiers == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "奖励梯度缺失");
        }
        for (PetSeasonReward tier : tiers) {
            if (tier == null || tier.getRankMin() == null || tier.getRankMax() == null) {
                throw new com.cloudmart.common.exception.BusinessException(
                        com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "奖励梯度区间非法（缺名次）");
            }
            if (tier.getRewardStarlight() != null && tier.getRewardStarlight() < 0
                    || tier.getRewardExp() != null && tier.getRewardExp() < 0) {
                throw new com.cloudmart.common.exception.BusinessException(
                        com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "奖励梯度禁止负值");
            }
        }
        List<PetSeasonReward> sorted = tiers.stream()
                .sorted(Comparator.comparingInt(PetSeasonReward::getRankMin))
                .toList();
        int expect = 1;
        for (PetSeasonReward tier : sorted) {
            if (tier.getRankMin() < 1 || tier.getRankMax() < tier.getRankMin()) {
                throw new com.cloudmart.common.exception.BusinessException(
                        com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                        "奖励梯度区间非法");
            }
            if (tier.getRankMin() != expect) {
                throw new com.cloudmart.common.exception.BusinessException(
                        com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                        "奖励梯度必须从第 1 名起连续覆盖（间隙/重叠都拒绝）");
            }
            expect = tier.getRankMax() + 1;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
