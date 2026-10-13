package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.config.PetProperties;
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
import com.cloudmart.pet.service.PetPveService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetPveBossVO;
import com.cloudmart.pet.vo.PetPveRunVO;
import com.cloudmart.pet.wallet.PetRewardApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 协作 PVE 副本（R03/R04 状态机重写）。
 *
 * <p>R03 轮次推进：run 行持有 turnNo/nextActorUserId/version——每次成功攻击
 * turnNo+1、行动权交给对方（对方倒下则存活者继续，全员倒下 FAILED）；
 * CAS (id, status='FIGHTING', version) 推进，日志条数不再决定轮次；
 * 回合明细落 pet_pve_round_log（uk run+turn+seq，request_key 重放原回合结果）；
 * 参与占用行 pet_pve_participant（uk user_id）保证一人一个活动副本，终态释放。
 * 伤害复用 {@link PetBattleEngine} 口径：开局 Fighter 快照 + run 种子 + 回合号
 * 决定生成随机序列，服务端掌握结果，前端不能提交胜负/伤害。</p>
 *
 * <p>R04 奖励：WON 后 run.reward_status=PENDING，由 {@link #settlePendingRewards()}
 * 定时/手动逐用户经 {@link PetRewardApplicationService#claim} 领奖（uk
 * user+PVE+runId+MAIN 保证每人每场至多一次；钱包异常整笔回滚待重试）；
 * 独立 PVE_REWARD 日配额，配额外仅参与不发币；串门配额不受影响。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetPveServiceImpl implements PetPveService {

    /** 内置 Boss 配置：code → [name, maxHp, attack, defense]（服务端权威，经 /bosses 下发） */
    private static final Map<String, BossSpec> BOSSES = Map.of(
            "forest_ogre", new BossSpec("森林巨魔", 600, 55, 30),
            "sand_worm", new BossSpec("流沙巨虫", 900, 70, 45),
            "void_knight", new BossSpec("虚空骑士", 1500, 95, 60));

    private static final String RULE_VERSION = "V1";
    private static final long OPEN_EXPIRE_HOURS = 24;
    private static final long TURN_DEADLINE_MINUTES = 10;
    private static final int MAX_ROUNDS = 100;

    private final PetPveRunMapper runMapper;
    private final PetPveParticipantMapper participantMapper;
    private final PetPveRoundLogMapper roundLogMapper;
    private final PetRewardClaimMapper rewardClaimMapper;
    private final PetService petService;
    private final PetMapper petMapper;
    private final PetStatsService statsService;
    private final PetQuotaService quotaService;
    private final PetRewardApplicationService rewardApplicationService;
    private final PetProperties properties;
    private final SecureRandom seedRandom = new SecureRandom();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${pet.pve.reward-coins:100}")
    long rewardCoins;

    /** R04：PVE 胜利收益日配额（独立于串门；配额外仅参与不发币） */
    @Value("${pet.pve.daily-reward-limit:3}")
    int pveDailyRewardLimit;

    /** R03：副本总时限（FIGHTING 超时批量收口为 FAILED） */
    @Value("${pet.pve.run-time-limit-minutes:120}")
    long runTimeLimitMinutes;

    // ==================== 查询 ====================

    @Override
    public List<PetPveBossVO> bosses() {
        return BOSSES.entrySet().stream()
                .map(e -> new PetPveBossVO(e.getKey(), e.getValue().name(),
                        e.getValue().maxHp(), e.getValue().attack(), e.getValue().defense()))
                .toList();
    }

    @Override
    public Page<PetPveRunVO> openRuns(int page, int size) {
        closeExpiredRuns();
        Page<PetPveRun> result = runMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<PetPveRun>()
                        .eq(PetPveRun::getStatus, PetPveRun.STATUS_OPEN)
                        .gt(PetPveRun::getCreatedAt, LocalDateTime.now().minusHours(OPEN_EXPIRE_HOURS))
                        .orderByDesc(PetPveRun::getId));
        return toVoPage(result);
    }

    @Override
    public Page<PetPveRunVO> mine(Long userId, int page, int size) {
        Page<PetPveRun> result = runMapper.selectPage(new Page<>(page, Math.min(size, 50)),
                new LambdaQueryWrapper<PetPveRun>()
                        .and(w -> w.eq(PetPveRun::getInitiatorUserId, userId)
                                .or().eq(PetPveRun::getPartnerUserId, userId))
                        .orderByDesc(PetPveRun::getId));
        return toVoPage(result);
    }

    @Override
    public PetPveRunVO detail(Long userId, Long runId) {
        PetPveRun run = requireRun(runId);
        boolean participant = run.getInitiatorUserId().equals(userId)
                || userId.equals(run.getPartnerUserId());
        if (!participant && !PetPveRun.STATUS_OPEN.equals(run.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_FORBIDDEN, "仅参与者可查看");
        }
        return toVO(run, userId, recentRounds(runId));
    }

    // ==================== 状态机写操作 ====================

    @Override
    @Transactional
    public PetPveRunVO start(Long userId, String bossCode) {
        // R19：能力开关——关闭时拒绝发起新副本；进行中副本的攻击/领奖不拦（已受理操作照常结算）
        if (!properties.getFeatureSwitches().isCooperativePve()) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "协作副本暂未开放");
        }
        BossSpec spec = BOSSES.get(bossCode);
        if (spec == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "未知的 Boss");
        }
        Pet pet = petService.requireOwnedPet(userId);
        // 占用行先行：uk(user_id) 数据库唯一约束保证一人一个活动副本（并发 start/join 只成功一个）
        PetPveParticipant occupation = new PetPveParticipant();
        occupation.setUserId(userId);
        occupation.setRunId(null);
        occupation.setPetId(pet.getId());
        try {
            participantMapper.insert(occupation);
        } catch (DuplicateKeyException occupied) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "你已有进行中的副本，先完成或等待收口");
        }
        PetPveRun run = new PetPveRun();
        run.setBossCode(bossCode);
        run.setBossName(spec.name());
        run.setBossMaxHp(spec.maxHp());
        run.setBossHp(spec.maxHp());
        run.setInitiatorUserId(userId);
        run.setInitiatorPetId(pet.getId());
        run.setInitiatorSnapshot(toJson(toFighter(pet)));
        run.setInitiatorPetHp(maxHpOf(pet));
        run.setStatus(PetPveRun.STATUS_OPEN);
        run.setVersion(0);
        run.setTurnNo(0);
        run.setRuleVersion(RULE_VERSION);
        run.setSeed(seedRandom.nextLong());
        run.setRewardStatus(PetPveRun.REWARD_NONE);
        run.setRunExpiresAt(LocalDateTime.now().plusHours(OPEN_EXPIRE_HOURS));
        try {
            runMapper.insert(run);
        } catch (Exception e) {
            // 副本行写入失败：释放占用行（同事务回滚亦可达此目的，显式删除防御半提交）
            participantMapper.deleteById(occupation.getId());
            throw e;
        }
        occupation.setRunId(run.getId());
        participantMapper.updateById(occupation);
        log.info("PVE 副本已发起: runId={}, boss={}, user={}, seed={}", run.getId(), bossCode, userId, run.getSeed());
        return toVO(runMapper.selectById(run.getId()), userId, List.of());
    }

    @Override
    @Transactional
    public PetPveRunVO join(Long userId, Long runId) {
        PetPveRun run = requireRun(runId);
        if (!PetPveRun.STATUS_OPEN.equals(run.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该副本已不在等待队友状态");
        }
        if (run.getInitiatorUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "这是你发起的副本");
        }
        Pet pet = petService.requireOwnedPet(userId);
        // 占用行先行：与 start 共用 uk(user_id)——已在别的副本中直接拒绝
        PetPveParticipant occupation = new PetPveParticipant();
        occupation.setUserId(userId);
        occupation.setRunId(runId);
        occupation.setPetId(pet.getId());
        try {
            participantMapper.insert(occupation);
        } catch (DuplicateKeyException occupied) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "你已有进行中的副本，先完成或等待收口");
        }
        // CAS：OPEN→FIGHTING（并发加入仅一人成功；失败释放自己刚占的位）
        int updated = runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                .eq(PetPveRun::getId, runId)
                .eq(PetPveRun::getStatus, PetPveRun.STATUS_OPEN)
                .set(PetPveRun::getPartnerUserId, userId)
                .set(PetPveRun::getPartnerPetId, pet.getId())
                .set(PetPveRun::getPartnerSnapshot, toJson(toFighter(pet)))
                .set(PetPveRun::getPartnerPetHp, maxHpOf(pet))
                .set(PetPveRun::getStatus, PetPveRun.STATUS_FIGHTING)
                .set(PetPveRun::getTurnNo, 0)
                .set(PetPveRun::getNextActorUserId, run.getInitiatorUserId())
                .set(PetPveRun::getTurnDeadlineAt, LocalDateTime.now().plusMinutes(TURN_DEADLINE_MINUTES))
                .set(PetPveRun::getRunExpiresAt, LocalDateTime.now().plusMinutes(runTimeLimitMinutes))
                .set(PetPveRun::getVersion, 1));
        if (updated == 0) {
            participantMapper.deleteById(occupation.getId());
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "手慢了，该副本刚被别人加入");
        }
        log.info("PVE 队友加入: runId={}, partner={}", runId, userId);
        return toVO(runMapper.selectById(runId), userId, List.of());
    }

    @Override
    @Transactional
    public PetPveRunVO cancel(Long userId, Long runId) {
        PetPveRun run = requireRun(runId);
        if (!run.getInitiatorUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_FORBIDDEN, "仅发起人可取消");
        }
        int updated = runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                .eq(PetPveRun::getId, runId)
                .eq(PetPveRun::getStatus, PetPveRun.STATUS_OPEN)
                .set(PetPveRun::getStatus, PetPveRun.STATUS_CANCELLED)
                .set(PetPveRun::getFinishedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "副本已开始或已结束，不能取消");
        }
        releaseParticipants(runId);
        return toVO(runMapper.selectById(runId), userId, List.of());
    }

    @Override
    @Transactional
    public PetPveRunVO attack(Long userId, Long runId, String requestKey) {
        PetPveRun run = requireRun(runId);
        if (!PetPveRun.STATUS_FIGHTING.equals(run.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "副本不在战斗中");
        }
        boolean isInitiator = run.getInitiatorUserId().equals(userId);
        boolean isPartner = userId.equals(run.getPartnerUserId());
        if (!isInitiator && !isPartner) {
            throw new BusinessException(PetErrorCodes.PET_FORBIDDEN, "你不是该副本的参与者");
        }
        String key = requestKey == null || requestKey.isBlank() ? "atk-" + UUID.randomUUID() : requestKey.trim();
        // 重放：同请求键的攻击已生效 → 返回当前战局（原回合结果，不重复推进）
        if (roundLogMapper.selectCount(new LambdaQueryWrapper<PetPveRoundLog>()
                .eq(PetPveRoundLog::getRunId, runId)
                .eq(PetPveRoundLog::getRequestKey, key)) > 0) {
            log.info("PVE 攻击重放返回原战局: runId={}, user={}, key={}", runId, userId, key);
            return toVO(run, userId, recentRounds(runId));
        }
        // 行动权判定：nextActorUserId 权威（并发/重放后轮次清晰）
        if (!userId.equals(run.getNextActorUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "还没轮到你的宠物行动");
        }
        if (run.getTurnNo() >= MAX_ROUNDS) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "回合数已达上限，副本将按失败收口");
        }

        // ---------- 决定性结算：开局快照 + seed + turnNo 派生随机序列 ----------
        Random rng = new Random(run.getSeed() + run.getTurnNo() * 31L);
        FighterBox actorBox = isInitiator
                ? new FighterBox(parseFighter(run.getInitiatorSnapshot()), run.getInitiatorPetHp())
                : new FighterBox(parseFighter(run.getPartnerSnapshot()), run.getPartnerPetHp());
        int bossHp = run.getBossHp();

        int turnNo = run.getTurnNo() + 1;
        List<PetPveRoundLog> logs = new java.util.ArrayList<>();
        PetBattleEngine.PveStrike strike = PetBattleEngine.pveStrike(actorBox.fighter(),
                BOSSES.get(run.getBossCode()).defense(), rng);
        bossHp = Math.max(0, bossHp - strike.damage());
        logs.add(logOf(run, turnNo, 1, userId, actorBox.fighter(), PetPveRoundLog.ACTION_ATTACK,
                strike, run.getBossName(), bossHp, key));

        int actorHpAfter = actorBox.hp();
        if (bossHp > 0) {
            // Boss 反击行动宠（存活时）；闪避按对战防守口径
            PetBattleEngine.PveStrike counter = PetBattleEngine.pveCounter(
                    BOSSES.get(run.getBossCode()).attack(), actorBox.fighter(), rng);
            actorHpAfter = Math.max(0, actorHpAfter - counter.damage());
            logs.add(logOf(run, turnNo, 2, null, null, PetPveRoundLog.ACTION_COUNTER,
                    counter, actorBox.fighter().name(), actorHpAfter, null));
        }

        // ---------- 终态/下一行动方 ----------
        int initiatorHpAfter = isInitiator ? actorHpAfter : run.getInitiatorPetHp();
        int partnerHpAfter = isInitiator ? run.getPartnerPetHp() : actorHpAfter;
        boolean initiatorAlive = initiatorHpAfter > 0;
        boolean partnerAlive = run.getPartnerPetId() != null && partnerHpAfter > 0;
        String nextStatus = run.getStatus();
        LocalDateTime finishedAt = null;
        if (bossHp <= 0) {
            nextStatus = PetPveRun.STATUS_WON;
            finishedAt = LocalDateTime.now();
        } else if (!initiatorAlive && !partnerAlive) {
            nextStatus = PetPveRun.STATUS_FAILED;
            finishedAt = LocalDateTime.now();
        }
        // 存活者继续：对方存活则轮对方；对方倒下由存活者连续行动
        Long nextActor = PetPveRun.STATUS_FIGHTING.equals(nextStatus)
                ? (otherAlive(isInitiator, initiatorAlive, partnerAlive) ? otherOf(run, userId) : userId)
                : null;

        // ---------- 回合明细先落（uk run+request 重放判重），CAS 推进 run 行 ----------
        try {
            logs.forEach(roundLogMapper::insert);
        } catch (DuplicateKeyException duplicate) {
            // 并发同请求键：视为重放，返回当前战局
            return toVO(runMapper.selectById(runId), userId, recentRounds(runId));
        }
        LambdaUpdateWrapper<PetPveRun> uw = new LambdaUpdateWrapper<PetPveRun>()
                .eq(PetPveRun::getId, runId)
                .eq(PetPveRun::getStatus, PetPveRun.STATUS_FIGHTING)
                .eq(PetPveRun::getVersion, run.getVersion())
                .set(PetPveRun::getBossHp, bossHp)
                .set(PetPveRun::getInitiatorPetHp, initiatorHpAfter)
                .set(PetPveRun::getPartnerPetHp, run.getPartnerPetId() == null ? null : partnerHpAfter)
                .set(PetPveRun::getTurnNo, turnNo)
                .set(PetPveRun::getNextActorUserId, nextActor)
                .setSql("version = version + 1");
        if (!PetPveRun.STATUS_FIGHTING.equals(nextStatus)) {
            uw.set(PetPveRun::getStatus, nextStatus);
            uw.set(PetPveRun::getFinishedAt, finishedAt);
        } else {
            uw.set(PetPveRun::getTurnDeadlineAt, LocalDateTime.now().plusMinutes(TURN_DEADLINE_MINUTES));
        }
        int updated = runMapper.update(null, uw);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "战斗状态刚被队友推进，请刷新后再攻击");
        }
        if (PetPveRun.STATUS_WON.equals(nextStatus)) {
            // R04：奖励交付异步可恢复——WON 先落 PENDING，定时/手动逐用户 claim（uk 防重）
            runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                    .eq(PetPveRun::getId, runId)
                    .eq(PetPveRun::getRewardStatus, PetPveRun.REWARD_NONE)
                    .set(PetPveRun::getRewardStatus, PetPveRun.REWARD_PENDING));
        }
        if (!PetPveRun.STATUS_FIGHTING.equals(nextStatus)) {
            releaseParticipants(runId);
        }
        return toVO(runMapper.selectById(runId), userId, recentRounds(runId));
    }

    // ==================== 定时收口与奖励补发 ====================

    @Override
    @Scheduled(fixedDelayString = "${pet.pve.sweep-delay-ms:30000}")
    @Transactional
    public int closeExpiredRuns() {
        int closed = 0;
        // FIGHTING 超总时限 → FAILED（收口不能把失败改写成胜利）
        List<PetPveRun> fighting = runMapper.selectList(new LambdaQueryWrapper<PetPveRun>()
                .eq(PetPveRun::getStatus, PetPveRun.STATUS_FIGHTING)
                .isNotNull(PetPveRun::getRunExpiresAt)
                .lt(PetPveRun::getRunExpiresAt, LocalDateTime.now())
                .last("LIMIT 50"));
        for (PetPveRun run : fighting) {
            int updated = runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                    .eq(PetPveRun::getId, run.getId())
                    .eq(PetPveRun::getStatus, PetPveRun.STATUS_FIGHTING)
                    .set(PetPveRun::getStatus, PetPveRun.STATUS_FAILED)
                    .set(PetPveRun::getFinishedAt, LocalDateTime.now()));
            if (updated == 1) {
                releaseParticipants(run.getId());
                closed++;
            }
        }
        // OPEN 超 24h → EXPIRED
        int expired = runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                .eq(PetPveRun::getStatus, PetPveRun.STATUS_OPEN)
                .lt(PetPveRun::getCreatedAt, LocalDateTime.now().minusHours(OPEN_EXPIRE_HOURS))
                .set(PetPveRun::getStatus, PetPveRun.STATUS_EXPIRED)
                .set(PetPveRun::getFinishedAt, LocalDateTime.now()));
        if (expired > 0) {
            closed += expired;
        }
        if (closed > 0) {
            log.info("PVE 过期副本已收口: count={}", closed);
        }
        return closed;
    }

    @Override
    @Scheduled(fixedDelayString = "${pet.pve.reward-sweep-delay-ms:15000}")
    public int settlePendingRewards() {
        List<PetPveRun> pending = runMapper.selectList(new LambdaQueryWrapper<PetPveRun>()
                .eq(PetPveRun::getStatus, PetPveRun.STATUS_WON)
                .eq(PetPveRun::getRewardStatus, PetPveRun.REWARD_PENDING)
                .last("LIMIT 20"));
        int settled = 0;
        for (PetPveRun run : pending) {
            if (settleRewards(run)) {
                settled++;
            }
        }
        return settled;
    }

    /**
     * R04：逐用户领奖（每次 claim 独立短事务——单人失败不回滚他人已交付事实）。
     * pet_reward_claim uk(user, PVE, runId, MAIN) 保证每人每场至多一次；
     * 配额外仅参与不发币（独立 PVE_REWARD 配额，不占串门）。
     *
     * @return 该副本奖励是否全部交付完成（全部 COMPLETED → run.reward_status=SETTLED）
     */
    private boolean settleRewards(PetPveRun run) {
        List<Long> userIds = run.getPartnerUserId() == null
                ? List.of(run.getInitiatorUserId())
                : List.of(run.getInitiatorUserId(), run.getPartnerUserId());
        boolean allCompleted = true;
        for (Long userId : userIds) {
            try {
                boolean completed = claimVictoryReward(run, userId);
                allCompleted = allCompleted && completed;
            } catch (Exception e) {
                // 钱包冻结/维护/异常：claim 内部事务回滚，reward_status 留 PENDING 等待补发
                log.warn("PVE 奖励交付未完成（可恢复重试）: runId={}, userId={}, err={}",
                        run.getId(), userId, e.getMessage());
                allCompleted = false;
            }
        }
        if (allCompleted) {
            int updated = runMapper.update(null, new LambdaUpdateWrapper<PetPveRun>()
                    .eq(PetPveRun::getId, run.getId())
                    .eq(PetPveRun::getRewardStatus, PetPveRun.REWARD_PENDING)
                    .set(PetPveRun::getRewardStatus, PetPveRun.REWARD_SETTLED));
            if (updated == 1) {
                log.info("PVE 胜利奖励全部到账: runId={}, users={}", run.getId(), userIds);
            }
        }
        return allCompleted;
    }

    /** 单用户领奖；返回是否 COMPLETED（重复调用命中已 COMPLETED 行返回 true，不重复发币） */
    private boolean claimVictoryReward(PetPveRun run, Long userId) {
        // 配额判定在 resolver 内（与领奖同事务：配额占用随 claim 失败一并回滚）
        rewardApplicationService.claim(
                new PetRewardApplicationService.RewardClaimCommand(userId, petIdOf(run, userId),
                        "PVE", String.valueOf(run.getId()), "MAIN", null),
                command -> {
                    boolean withinQuota = quotaService.tryConsume(userId,
                            PetQuotaService.QuotaType.PVE_REWARD, 0, pveDailyRewardLimit);
                    // R04 默认口径：配额外仅参与、不发货币（无保底参与奖）
                    long coins = withinQuota ? rewardCoins : 0;
                    return new PetRewardApplicationService.ResolvedReward(coins, "",
                            List.of(), RULE_VERSION);
                });
        return "COMPLETED".equals(claimStatus(run.getId(), userId));
    }

    private String claimStatus(Long runId, Long userId) {
        PetRewardClaim claim = rewardClaimMapper.selectOne(new LambdaQueryWrapper<PetRewardClaim>()
                .eq(PetRewardClaim::getBizType, "PVE")
                .eq(PetRewardClaim::getBizId, String.valueOf(runId))
                .eq(PetRewardClaim::getUserId, userId)
                .last("LIMIT 1"));
        return claim == null ? null : claim.getStatus();
    }

    // ==================== 内部 ====================

    private void releaseParticipants(Long runId) {
        participantMapper.delete(new LambdaQueryWrapper<PetPveParticipant>()
                .eq(PetPveParticipant::getRunId, runId));
    }

    private boolean otherAlive(boolean isInitiator, boolean initiatorAlive, boolean partnerAlive) {
        return isInitiator ? partnerAlive : initiatorAlive;
    }

    private Long otherOf(PetPveRun run, Long userId) {
        return userId.equals(run.getInitiatorUserId()) ? run.getPartnerUserId() : run.getInitiatorUserId();
    }

    private Long petIdOf(PetPveRun run, Long userId) {
        return userId.equals(run.getInitiatorUserId()) ? run.getInitiatorPetId() : run.getPartnerPetId();
    }

    private PetPveRoundLog logOf(PetPveRun run, int turnNo, int seq, Long actorUserId,
                                 PetBattleEngine.Fighter actor, String action,
                                 PetBattleEngine.PveStrike strike, String targetName,
                                 int targetHpAfter, String requestKey) {
        PetPveRoundLog logEntry = new PetPveRoundLog();
        logEntry.setRunId(run.getId());
        logEntry.setTurnNo(turnNo);
        logEntry.setSeq(seq);
        logEntry.setActorUserId(actorUserId);
        logEntry.setActorPetId(actor == null ? null : actor.petId());
        logEntry.setActorName(actor == null ? run.getBossName() : actor.name());
        logEntry.setAction(action);
        logEntry.setDamage(strike.damage());
        logEntry.setCritical(strike.critical());
        logEntry.setDodged(strike.dodged());
        logEntry.setTargetName(targetName);
        logEntry.setTargetHpAfter(targetHpAfter);
        logEntry.setRequestKey(requestKey);
        return logEntry;
    }

    private List<PetPveRunVO.RoundLog> recentRounds(Long runId) {
        List<PetPveRoundLog> rows = roundLogMapper.selectList(new LambdaQueryWrapper<PetPveRoundLog>()
                .eq(PetPveRoundLog::getRunId, runId)
                .orderByDesc(PetPveRoundLog::getTurnNo)
                .orderByAsc(PetPveRoundLog::getSeq)
                .last("LIMIT 10"));
        return rows.stream().map(r -> new PetPveRunVO.RoundLog(r.getTurnNo(), r.getActorName(),
                r.getAction(), r.getDamage(), Boolean.TRUE.equals(r.getCritical()),
                Boolean.TRUE.equals(r.getDodged()), r.getTargetName(), r.getTargetHpAfter())).toList();
    }

    private PetPveRun requireRun(Long runId) {
        PetPveRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "副本不存在");
        }
        return run;
    }

    private PetBattleEngine.Fighter toFighter(Pet pet) {
        PetStatsService.CombatStats stats = statsService.combatStats(pet);
        // 全参口径（对齐 PetBattleServiceImpl.toFighter）：含装备/技能加成
        return new PetBattleEngine.Fighter(pet.getId(), pet.getName(),
                stats.hp(), stats.maxHp(), stats.strength(), stats.intelligence(), stats.agility(), stats.charm(),
                stats.critBonus(), stats.dodgeBonus(), stats.damageBonus(),
                stats.powerStrikeBonus(), stats.damageReduction(), statsService.firstStrikeBonus(pet));
    }

    private PetBattleEngine.Fighter parseFighter(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, PetBattleEngine.Fighter.class);
        } catch (Exception e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "战斗快照损坏，无法继续战斗");
        }
    }

    private int maxHpOf(Pet pet) {
        return statsService.combatStats(pet).maxHp();
    }

    private Page<PetPveRunVO> toVoPage(Page<PetPveRun> result) {
        Page<PetPveRunVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        // R30：整页宠物名一次批量加载（原逐行 2 次 selectById，每页最多 2N 次查询）
        List<PetPveRun> runs = result.getRecords();
        Map<Long, String> petNames = petNamesById(runs.stream()
                .flatMap(run -> Stream.of(run.getInitiatorPetId(), run.getPartnerPetId()))
                .toList());
        voPage.setRecords(runs.stream().map(r -> toVO(r, null, List.of(), petNames)).toList());
        return voPage;
    }

    private PetPveRunVO toVO(PetPveRun run, Long viewerId, List<PetPveRunVO.RoundLog> rounds) {
        return toVO(run, viewerId, rounds,
                petNamesById(List.of(run.getInitiatorPetId(), run.getPartnerPetId())));
    }

    private PetPveRunVO toVO(PetPveRun run, Long viewerId, List<PetPveRunVO.RoundLog> rounds,
                             Map<Long, String> petNames) {
        Long nextActor = run.getNextActorUserId();
        String blockedReason = null;
        boolean canAttack = false;
        if (viewerId != null && PetPveRun.STATUS_FIGHTING.equals(run.getStatus())) {
            if (!viewerId.equals(nextActor)) {
                blockedReason = "NOT_YOUR_TURN";
            } else if (run.getTurnDeadlineAt() != null && run.getTurnDeadlineAt().isBefore(LocalDateTime.now())) {
                blockedReason = "TURN_OVERDUE";
            } else {
                canAttack = true;
            }
        }
        Integer initiatorMaxHp = maxHpFromSnapshot(run.getInitiatorSnapshot());
        Integer partnerMaxHp = run.getPartnerSnapshot() == null ? null : maxHpFromSnapshot(run.getPartnerSnapshot());
        return new PetPveRunVO(
                run.getId(), run.getBossCode(), run.getBossName(),
                run.getBossMaxHp(), run.getBossHp(),
                run.getInitiatorUserId(), petNames.get(run.getInitiatorPetId()),
                run.getPartnerUserId(), petNames.get(run.getPartnerPetId()),
                run.getInitiatorPetHp(), run.getPartnerPetHp() == null ? -1 : run.getPartnerPetHp(),
                run.getStatus(), PetPveRun.REWARD_SETTLED.equals(run.getRewardStatus()), rounds,
                run.getCreatedAt(), run.getFinishedAt(),
                run.getTurnNo() == null ? 0 : run.getTurnNo(), nextActor, canAttack, blockedReason,
                run.getTurnDeadlineAt(), run.getRunExpiresAt(), run.getRewardStatus(),
                initiatorMaxHp == null ? 0 : initiatorMaxHp, partnerMaxHp == null ? 0 : partnerMaxHp);
    }

    private Integer maxHpFromSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, PetBattleEngine.Fighter.class).maxHp();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * R30：按宠物 ID 批量加载名称（单次 IN 查询）。
     * 供列表整页 enrich 与单局详情复用，替代逐行 {@code selectById} 的 N+1 查询。
     */
    private Map<Long, String> petNamesById(Collection<Long> petIds) {
        List<Long> distinctIds = petIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return petMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Pet::getId, Pet::getName, (first, second) -> first));
    }

    private String toJson(PetBattleEngine.Fighter fighter) {
        try {
            return objectMapper.writeValueAsString(fighter);
        } catch (Exception e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "战斗快照序列化失败");
        }
    }

    private record FighterBox(PetBattleEngine.Fighter fighter, int hp) {
    }

    /** 内置 Boss 配置 */
    private record BossSpec(String name, int maxHp, int attack, int defense) {}
}
