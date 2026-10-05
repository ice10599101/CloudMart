package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonReward;
import com.cloudmart.pet.entity.PetSeasonSettlementJob;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRewardMapper;
import com.cloudmart.pet.repository.PetSeasonSettlementJobMapper;
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
 * 赛季结算协调器（F2/R06/PET-02）：状态机调度、租约与游标推进；冻结/发奖的
 * <strong>事务原子性</strong>委托给 {@link PetSeasonSettlementTxWorker}（独立 Bean 走真实
 * Spring 代理，修复原同类自调用 @Transactional 失效）。
 *
 * <p>状态机：ACTIVE →(到期 CAS)→ FREEZING →(一次一致性快照同事务)→ SETTLING →(游标推进完)→ SETTLED。
 * 失败不回退 ACTIVE：错误记入作业行，租约到期由调度接管——FREEZING 无快照/无作业均可幂等重冻榜，
 * 不再滞留"只找作业的空循环"。</p>
 *
 * <p>租约 fence：每次抢占返回 (owner, leaseVersion)，此后游标推进、错误记录、完成标记全部携带
 * fence 条件；旧执行者一旦失去租约立即停止，防双实例重复入账（行级奖励 CAS 为最终兜底）。</p>
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
    private final PetSeasonSettlementJobMapper jobMapper;
    private final PetProperties properties;
    private final PetSeasonSettlementTxWorker txWorker;
    private final JdbcTemplate jdbcTemplate;
    /** 执行者唯一标识：每次租约获取以此写入作业行，fence 校验的 owner 半边（UUID 保证跨实例唯一） */
    private final String leaseOwner = UUID.randomUUID().toString();

    /** 每分钟驱动到期/中断赛季（调度器调用）：CAS 占 FREEZING → 冻榜 → 续跑发奖 */
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

    /**
     * 管理端手动触发（R06/PET-02）：校验到期后只把状态机推进到 SETTLING 并建立作业，返回作业行；
     * 分批发奖由调度器按作业游标续跑，不再在管理请求线程内同步结完全部名次。
     */
    public PetSeasonSettlementJob requestSettlement(Long seasonId) {
        PetSeason season = seasonMapper.selectById(seasonId);
        if (season == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "赛季不存在");
        }
        if ("SETTLED".equals(season.getStatus())) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "赛季已结算完成");
        }
        if (season.getEndsAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "赛季尚未到期");
        }
        driveSeason(season);
        return jobMapper.selectOne(new LambdaQueryWrapper<PetSeasonSettlementJob>()
                .eq(PetSeasonSettlementJob::getSeasonId, seasonId));
    }

    /** 状态机驱动：ACTIVE→冻榜；FREEZING→幂等重冻榜；SETTLING→续跑发奖 */
    private void driveSeason(PetSeason season) {
        switch (season.getStatus()) {
            case "ACTIVE" -> {
                int claimed = seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                        .set(PetSeason::getStatus, "FREEZING")
                        .eq(PetSeason::getId, season.getId())
                        .eq(PetSeason::getStatus, "ACTIVE"));
                if (claimed > 0) {
                    txWorker.freezeInTx(seasonMapper.selectById(season.getId()));
                }
                // 冻榜失败（快照未完整）停留 FREEZING，下轮重入；错误已记录
            }
            case "FREEZING" -> txWorker.freezeInTx(season);
            case "SETTLING" -> settleFromJob(seasonMapper.selectById(season.getId()));
            default -> {
                // SETTLED：无需处理
            }
        }
    }

    /** SETTLING 驱动：抢/续作业租约 → 按快照游标分批发奖 → 游标到底 CAS SETTLED */
    private void settleFromJob(PetSeason season) {
        PetSeasonSettlementJob job = jobMapper.selectOne(new LambdaQueryWrapper<PetSeasonSettlementJob>()
                .eq(PetSeasonSettlementJob::getSeasonId, season.getId()));
        if (job == null) {
            // SETTLING 但无作业行（异常态）：回退 FREEZING，下轮 driveSeason 幂等重冻榜——
            // 修复原实现"回退后仍只找作业"导致 FREEZING 永久空转
            seasonMapper.update(null, new LambdaUpdateWrapper<PetSeason>()
                    .set(PetSeason::getStatus, "FREEZING")
                    .set(PetSeason::getSnapshotComplete, 0)
                    .eq(PetSeason::getId, season.getId())
                    .eq(PetSeason::getStatus, "SETTLING"));
            log.warn("赛季 SETTLING 无作业行，已回退 FREEZING 重冻榜: seasonId={}", season.getId());
            return;
        }
        if ("COMPLETED".equals(job.getStatus())) {
            finalizeSeason(season, job, null);
            return;
        }
        Lease lease = tryClaimLease(job);
        if (lease == null) {
            return;
        }
        List<PetSeasonReward> tiers = rewardMapper.selectList(new LambdaQueryWrapper<PetSeasonReward>()
                .eq(PetSeasonReward::getSeasonId, season.getId())
                .orderByAsc(PetSeasonReward::getRankMin));
        while (job.getCursorRank() < job.getTotalCount()) {
            PetSeasonSettlementTxWorker.BatchOutcome outcome;
            try {
                outcome = txWorker.settleBatchInTx(season, tiers, job.getCursorRank(), job.getTotalCount(), SETTLE_BATCH);
            } catch (Exception e) {
                // 可重试失败：fence 内记录错误与重试时间；游标未推进，下轮从原位续跑
                recordBatchFailure(lease, e);
                log.error("赛季发奖批次失败（游标未推进，租约到期续跑）: seasonId={}, cursor={}",
                        season.getId(), job.getCursorRank(), e);
                return;
            }
            if (outcome == null) {
                // 防御：快照行少于 totalCount（不应发生）——停止推进避免死循环，人工核查
                recordBatchFailure(lease, new IllegalStateException("快照行数少于 totalCount，游标无行可推进"));
                return;
            }
            try {
                advanceCursor(lease, outcome.processedToRank(), outcome.rewarded());
            } catch (LeaseLostException e) {
                // 旧执行者失去租约立即停止；新执行者从持久化游标继续
                log.info("赛季租约已被接管，本执行者停止: seasonId={}, cursor={}",
                        season.getId(), outcome.processedToRank());
                return;
            }
            job.setCursorRank(outcome.processedToRank());
            job.setSuccessCount(job.getSuccessCount() + outcome.rewarded());
        }
        PetSeasonSettlementJob latest = jobMapper.selectById(job.getId());
        if (latest.getCursorRank() >= latest.getTotalCount()) {
            finalizeSeason(season, latest, lease);
        }
    }

    /**
     * CAS 抢/续租约（仅当到期或空闲）；胜者持有 (owner, leaseVersion) fence 直到失去租约。
     *
     * @return 租约凭证；抢不到返回 null
     */
    private Lease tryClaimLease(PetSeasonSettlementJob job) {
        int claimed = jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getLeaseOwner, leaseOwner)
                .set(PetSeasonSettlementJob::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS))
                .setSql("lease_version = lease_version + 1")
                .eq(PetSeasonSettlementJob::getId, job.getId())
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING")
                .and(w -> w.isNull(PetSeasonSettlementJob::getLeaseUntil)
                        .or().le(PetSeasonSettlementJob::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC))));
        if (claimed != 1) {
            return null;
        }
        // 条件 UPDATE（owner+版本自增）是租约归属的权威；重读仅为取回 fence 基线 leaseVersion
        PetSeasonSettlementJob claimedJob = jobMapper.selectById(job.getId());
        if (claimedJob == null) {
            return null;
        }
        return new Lease(job.getId(), leaseOwner, claimedJob.getLeaseVersion());
    }

    /** 批事务提交后由驱动方推进游标（checkpoint 只在事实提交后前进）；fence 校验 owner+version */
    private void advanceCursor(Lease lease, int processedToRank, int rewarded) {
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getCursorRank, processedToRank)
                .setSql("success_count = success_count + " + Math.max(rewarded, 0))
                .set(PetSeasonSettlementJob::getLeaseUntil, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS))
                .eq(PetSeasonSettlementJob::getId, lease.jobId())
                .eq(PetSeasonSettlementJob::getLeaseOwner, lease.owner())
                .eq(PetSeasonSettlementJob::getLeaseVersion, lease.fenceVersion())
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING"));
        if (updated == 0) {
            throw new LeaseLostException();
        }
    }

    /** 批次失败记录（fence 内）：失去租约的执行者不得覆盖新执行者的作业行 */
    private void recordBatchFailure(Lease lease, Exception cause) {
        jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getLastError, truncate(String.valueOf(cause.getMessage())))
                .set(PetSeasonSettlementJob::getNextRetryAt, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(60))
                .eq(PetSeasonSettlementJob::getId, lease.jobId())
                .eq(PetSeasonSettlementJob::getLeaseOwner, lease.owner())
                .eq(PetSeasonSettlementJob::getLeaseVersion, lease.fenceVersion())
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING"));
    }

    /** 游标到底：作业 CAS RUNNING→COMPLETED + 赛季 CAS SETTLING→SETTLED（仅 snapshotComplete=1）；lease 到位时带 fence */
    private void finalizeSeason(PetSeason season, PetSeasonSettlementJob job, Lease lease) {
        LambdaUpdateWrapper<PetSeasonSettlementJob> complete = new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getStatus, "COMPLETED")
                .eq(PetSeasonSettlementJob::getId, job.getId())
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING");
        if (lease != null) {
            complete.eq(PetSeasonSettlementJob::getLeaseOwner, lease.owner())
                    .eq(PetSeasonSettlementJob::getLeaseVersion, lease.fenceVersion());
        }
        jobMapper.update(null, complete);
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

    /**
     * 管理端重试（PET-02）：清错误标记并立即驱动一轮续跑。已有成功奖励不可重发——
     * 重放由行级奖励 CAS + 钱包幂等键收敛，仅补未完成名次。
     */
    public PetSeasonSettlementJob retrySettlement(Long seasonId, Long jobId) {
        PetSeason season = seasonMapper.selectById(seasonId);
        if (season == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "赛季不存在");
        }
        int cleared = jobMapper.update(null, new LambdaUpdateWrapper<PetSeasonSettlementJob>()
                .set(PetSeasonSettlementJob::getLastError, null)
                .set(PetSeasonSettlementJob::getNextRetryAt, null)
                .eq(PetSeasonSettlementJob::getId, jobId)
                .eq(PetSeasonSettlementJob::getSeasonId, seasonId)
                .eq(PetSeasonSettlementJob::getStatus, "RUNNING")
                .isNotNull(PetSeasonSettlementJob::getLastError));
        if (cleared == 0) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_STATE_CONFLICT,
                    "作业不存在、已完成或无失败记录（仅失败批次可重试）");
        }
        driveSeason(season);
        return jobMapper.selectById(jobId);
    }

    /**
     * R17/PET-02 奖励梯度原子替换：锁住赛季行后<strong>重新读取</strong>状态校验（原实现信任
     * 调用方锁前传入的 status，存在 TOCTOU：校验通过后赛季被并发冻结仍被改梯度）。
     * delete+insert 同事务，中途失败整体回滚；已冻结档位只读。
     */
    @Transactional
    public void replaceTiersGuarded(Long seasonId, List<PetSeasonReward> tiers) {
        jdbcTemplate.queryForMap("SELECT id FROM pet_season WHERE id = ? FOR UPDATE", seasonId);
        PetSeason season = seasonMapper.selectById(seasonId);
        if (season == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "赛季不存在");
        }
        if (!"ACTIVE".equals(season.getStatus())) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR,
                    "当前赛季状态（" + season.getStatus() + "）不可修改奖励梯度");
        }
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

    /** 租约凭证：作业行 + 持有者 + 抢占时的 leaseVersion（fence，旧执行者更新不再命中） */
    record Lease(Long jobId, String owner, long fenceVersion) {
    }

    /** 失去租约：协调器立即停止本执行者的推进（游标已持久化，新执行者续跑） */
    static final class LeaseLostException extends RuntimeException {
        LeaseLostException() {
            super("赛季结算租约已被其他执行者接管");
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
