package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetMinigameOperation;
import com.cloudmart.pet.entity.PetMinigameRound;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetMinigameOperationMapper;
import com.cloudmart.pet.repository.PetMinigameRoundMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.service.PetUserGuardService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接球小游戏应用服务（R27 从 PetPlayFeatureService 拆分）：回合生命周期、
 * 窗口操作唯一事实、幂等结算与断线恢复。规则与时窗服务端权威（§7.4）。
 */
@Service
@Slf4j
public class PetMinigameService {

    private static final int CATCH_WINDOWS = 10;
    private static final long ROUND_SECONDS = 30;
    /** R11：单窗时长（毫秒）——窗口归属由服务端时钟决定，与点击次数无关 */
    private static final long WINDOW_MS = 3000;
    private static final long GRACE_SECONDS = 2;
    private static final int MIN_SUCCESS_FOR_REWARD = 3;
    private static final int DAILY_REWARD_ROUNDS = 5;
    private static final List<String> SLOTS = List.of("LEFT", "CENTER", "RIGHT");

    private final PetMapper petMapper;
    private final PetClock petClock;
    private final PetQuotaService quotaService;
    private final PetMinigameRoundMapper minigameMapper;
    private final com.cloudmart.pet.repository.PetMinigameOperationMapper operationMapper;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final com.cloudmart.pet.service.PetUserGuardService guardService;
    private final PetActivityMutex activityMutex;
    private final com.cloudmart.pet.service.impl.PetStateService stateService;
    private final com.cloudmart.pet.service.PetIntimacyService intimacyService;

    public PetMinigameService(PetMapper petMapper, PetClock petClock, PetQuotaService quotaService,
                              PetMinigameRoundMapper minigameMapper,
                              com.cloudmart.pet.repository.PetMinigameOperationMapper operationMapper,
                              com.cloudmart.pet.config.PetProperties properties,
                              PetUserGuardService guardService,
                              PetActivityMutex activityMutex,
                              com.cloudmart.pet.service.impl.PetStateService stateService,
                              com.cloudmart.pet.service.PetIntimacyService intimacyService) {
        this.petMapper = petMapper;
        this.petClock = petClock;
        this.quotaService = quotaService;
        this.minigameMapper = minigameMapper;
        this.operationMapper = operationMapper;
        this.properties = properties;
        this.guardService = guardService;
        this.activityMutex = activityMutex;
        this.stateService = stateService;
        this.intimacyService = intimacyService;
    }

    // ---------------- N04 接球小游戏 ----------------

    /** 开始一局（§7.4）：显式 petId 归属校验（不回退主宠）；guard 锁内检查 active 局与额度 */
    @Transactional
    public Map<String, Object> startRound(Long userId, Long petId) {
        if (!properties.getFeatureSwitches().isMinigame()) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能为自己的宠物开局");
        }
        guardService.lockGuard(userId);
        // R11：先惰性结算到期残留局——只剩过期局不得永久阻止开新局（断线恢复 T21）
        settleExpiredRounds(userId);
        // R11/R12：统一互斥——托管/长期活动进行中不开新局（有收益小游戏属互斥集合）
        activityMutex.requireFree(userId);
        Long active = minigameMapper.selectCount(new LambdaQueryWrapper<PetMinigameRound>()
                .eq(PetMinigameRound::getUserId, userId)
                .eq(PetMinigameRound::getStatus, "ACTIVE"));
        if (active > 0) {
            throw new BusinessException(PetErrorCodes.PET_USER_BUSY, "已有一局进行中");
        }
        if (pet.getEnergy() < 15) {
            throw new BusinessException(PetErrorCodes.PET_ENERGY_INSUFFICIENT, "宠物没有力气玩了");
        }
        LocalDate quotaDate = petClock.businessDate();
        // 收益局：消耗小游戏局数额度 + 玩耍额度（BE-08：分别记录占用，训练局只退还实际占用的，
        // 不再无条件 release PLAY 造成"释放未占用额度"或"训练局白烧 MINIGAME 配额"）
        boolean minigameConsumed = quotaService.tryConsume(userId, PetQuotaService.QuotaType.MINIGAME, 0, DAILY_REWARD_ROUNDS);
        boolean playConsumed = minigameConsumed
                && quotaService.tryConsume(userId, PetQuotaService.QuotaType.PLAY_REWARD, 0, 10);
        boolean rewardEligible = minigameConsumed && playConsumed;
        if (minigameConsumed && !playConsumed) {
            quotaService.release(userId, PetQuotaService.QuotaType.MINIGAME, 0);
        }
        // 随机挑战序列：10 个窗口，每窗随机左/中/右
        SecureRandom random = new SecureRandom();
        List<String> sequence = new ArrayList<>();
        for (int i = 0; i < CATCH_WINDOWS; i++) {
            sequence.add(SLOTS.get(random.nextInt(SLOTS.size())));
        }
        LocalDateTime now = petClock.nowUtc();
        PetMinigameRound round = new PetMinigameRound();
        round.setUserId(userId);
        round.setPetId(pet.getId());
        round.setGameType("CATCH");
        round.setStatus("ACTIVE");
        round.setRuleVersion("V1");
        round.setStartedAt(now);
        round.setDeadlineAt(now.plusSeconds(ROUND_SECONDS));
        round.setSequence(PetJsonUtils.toJson(sequence));
        round.setOps("[]");
        round.setSuccessCount(0);
        round.setRewardEligible(rewardEligible);
        round.setQuotaDate(quotaDate);
        try {
            minigameMapper.insert(round);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_USER_BUSY, "已有一局进行中");
        }
        if (rewardEligible) {
            // 有收益局扣精力（训练局不动属性）
            petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                    .setSql("energy = GREATEST(energy - 15, 0)")
                    .eq(Pet::getId, pet.getId()));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("roundId", round.getId());
        result.put("rewardEligible", rewardEligible);
        result.put("deadlineAt", round.getDeadlineAt());
        result.put("ruleVersion", round.getRuleVersion());
        // §7.4：目标序列是展示数据（防滥用靠服务端时窗/去重/额度，不靠序列保密）
        result.put("sequence", sequence);
        // R11：客户端时钟校准——窗口归属以服务端时间为准（windowIndex=floor((serverNow-startedAt)/windowMs)）
        result.put("startedAt", round.getStartedAt());
        result.put("serverNow", round.getStartedAt());
        result.put("windowMs", WINDOW_MS);
        return result;
    }

    /**
     * R11 当前局查询（断线恢复，§7.2 GET /minigames/current）：同 roundId 恢复；
     * 已到期残留局先惰性结算（到期自动完成，不永久卡 ACTIVE）。
     */
    @Transactional
    public Map<String, Object> currentRound(Long userId) {
        settleExpiredRounds(userId);
        PetMinigameRound round = minigameMapper.selectOne(new LambdaQueryWrapper<PetMinigameRound>()
                .eq(PetMinigameRound::getUserId, userId)
                .eq(PetMinigameRound::getStatus, "ACTIVE")
                .orderByDesc(PetMinigameRound::getId)
                .last("LIMIT 1"));
        Map<String, Object> result = new HashMap<>();
        if (round == null) {
            result.put("round", null);
            return result;
        }
        result.put("round", roundView(round));
        return result;
    }

    /** 对局视图：恢复所需的服务端权威字段（序列/已接受窗口/时间校准） */
    private Map<String, Object> roundView(PetMinigameRound round) {
        List<String> sequence = PetJsonUtils.parse(round.getSequence(),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                });
        Map<String, Object> view = new HashMap<>();
        view.put("roundId", round.getId());
        view.put("petId", round.getPetId());
        view.put("startedAt", round.getStartedAt());
        view.put("deadlineAt", round.getDeadlineAt());
        view.put("serverNow", petClock.nowUtc());
        view.put("windowMs", WINDOW_MS);
        view.put("rewardEligible", Boolean.TRUE.equals(round.getRewardEligible()));
        view.put("sequence", sequence);
        view.put("acceptedWindows", acceptedWindows(round.getId()));
        return view;
    }

    /** 已接受窗口序号（操作事实表权威） */
    private List<Integer> acceptedWindows(Long roundId) {
        return operationMapper.selectList(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetMinigameOperation>()
                        .eq(com.cloudmart.pet.entity.PetMinigameOperation::getRoundId, roundId)
                        .orderByAsc(com.cloudmart.pet.entity.PetMinigameOperation::getWindowIndex))
                .stream().map(com.cloudmart.pet.entity.PetMinigameOperation::getWindowIndex).toList();
    }

    /** R11：惰性结算该用户全部到期残留局（CAS 幂等；到期自动完成，不卡 ACTIVE） */
    private void settleExpiredRounds(Long userId) {
        List<PetMinigameRound> stale = minigameMapper.selectList(new LambdaQueryWrapper<PetMinigameRound>()
                .eq(PetMinigameRound::getUserId, userId)
                .eq(PetMinigameRound::getStatus, "ACTIVE")
                .le(PetMinigameRound::getDeadlineAt, petClock.nowUtc()));
        for (PetMinigameRound round : stale) {
            try {
                settleById(round);
            } catch (Exception e) {
                log.warn("到期局惰性结算失败（下轮重试）, roundId={}", round.getId(), e);
            }
        }
    }

    /**
     * 提交操作批次（R11 重写）：每窗操作以 pet_minigame_operation 唯一事实落库
     * （uk roundId+windowIndex——并发提交/重放由唯一键收敛，不再整行 JSON 读改写；
     * 原实现 updateById 全实体覆盖无版本，与 settle 竞争时旧 ACTIVE 实体可把
     * SETTLED 写回 ACTIVE）。窗口归属由服务端接收时间决定，客户端点击次数不参与。
     */
    @Transactional
    public Map<String, Object> submitOps(Long userId, Long roundId, List<Map<String, Object>> ops) {
        PetMinigameRound round = requireActiveRound(userId, roundId);
        if (ops == null || ops.isEmpty() || ops.size() > 10) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "操作批次需 1~10 条");
        }
        List<String> sequence = PetJsonUtils.parse(round.getSequence(),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                });
        LocalDateTime now = petClock.nowUtc();
        int acceptedNow = 0;
        for (Map<String, Object> op : ops) {
            Object rawWindow = op.get("windowIndex");
            Object rawSlot = op.get("slot");
            if (rawWindow == null || rawSlot == null) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "操作缺少 windowIndex/slot");
            }
            int window = ((Number) rawWindow).intValue();
            String slot = String.valueOf(rawSlot);
            if (window < 1 || window > CATCH_WINDOWS) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "窗口序号越界");
            }
            if (!SLOTS.contains(slot)) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "槽位非法");
            }
            // 服务端窗口判定：接收时间必须落在窗口内（相邻窗口 GRACE 宽限）；目标匹配随机序列
            long windowStartOffset = (window - 1) * (WINDOW_MS / 1000);
            LocalDateTime windowStart = round.getStartedAt().plusSeconds(windowStartOffset);
            LocalDateTime windowEnd = window == CATCH_WINDOWS
                    ? round.getDeadlineAt() : round.getStartedAt().plusSeconds(windowStartOffset + WINDOW_MS / 1000 + GRACE_SECONDS);
            boolean inWindow = !now.isBefore(windowStart) && !now.isAfter(windowEnd);
            boolean targetMatched = sequence.get(window - 1).equals(slot);
            if (!inWindow || !targetMatched) {
                continue;
            }
            // 每窗唯一事实：并发提交同一窗口只有一个胜者（DuplicateKey = 已接受，幂等跳过）
            try {
                com.cloudmart.pet.entity.PetMinigameOperation operation = new com.cloudmart.pet.entity.PetMinigameOperation();
                operation.setRoundId(roundId);
                operation.setUserId(userId);
                operation.setWindowIndex(window);
                operation.setSlot(slot);
                operation.setAccepted(1);
                operation.setServerTime(now);
                operationMapper.insert(operation);
                acceptedNow++;
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                // 该窗已被接受：幂等跳过，不重复计分
            }
        }
        int totalAccepted = acceptedCount(roundId);
        // 展示投影更新：LambdaUpdateWrapper 限定列+状态守卫——绝不触碰 status（终态不可被旧实体覆盖）
        minigameMapper.update(null, new LambdaUpdateWrapper<PetMinigameRound>()
                .set(PetMinigameRound::getOps, PetJsonUtils.toJson(Map.of("acceptedCount", totalAccepted)))
                .set(PetMinigameRound::getSuccessCount, totalAccepted)
                .eq(PetMinigameRound::getId, roundId)
                .eq(PetMinigameRound::getStatus, "ACTIVE"));
        Map<String, Object> result = new HashMap<>();
        result.put("accepted", acceptedNow);
        result.put("totalAccepted", totalAccepted);
        result.put("status", "ACTIVE");
        return result;
    }

    /** 已接受窗口数（操作事实表权威） */
    private int acceptedCount(Long roundId) {
        Long count = operationMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetMinigameOperation>()
                .eq(com.cloudmart.pet.entity.PetMinigameOperation::getRoundId, roundId));
        return count != null ? count.intValue() : 0;
    }

    /** 结束/到期结算（幂等 CAS SETTLED；正常结束须达服务端截止时间） */
    @Transactional
    public Map<String, Object> settle(Long userId, Long roundId) {
        PetMinigameRound round = minigameMapper.selectOne(new LambdaQueryWrapper<PetMinigameRound>()
                .eq(PetMinigameRound::getId, roundId)
                .eq(PetMinigameRound::getUserId, userId));
        if (round == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "对局不存在");
        }
        LocalDateTime now = petClock.nowUtc();
        if ("ACTIVE".equals(round.getStatus()) && round.getDeadlineAt().isAfter(now)) {
            // 截止前调用结束只返回进行中状态
            Map<String, Object> result = new HashMap<>();
            result.put("status", "ACTIVE");
            result.put("remainingSeconds", Duration.between(now, round.getDeadlineAt()).getSeconds());
            return result;
        }
        return settleById(round);
    }

    /** R11 结算主体（startRound 惰性结算复用）：操作事实计数为权威，CAS 幂等，奖励只发一次 */
    private Map<String, Object> settleById(PetMinigameRound round) {
        Long roundId = round.getId();
        // 操作事实为权威：以事实表计数覆盖（提交路径崩溃时投影列可能落后）
        int authoritativeCount = acceptedCount(roundId);
        if (!Integer.valueOf(authoritativeCount).equals(round.getSuccessCount())) {
            minigameMapper.update(null, new LambdaUpdateWrapper<PetMinigameRound>()
                    .set(PetMinigameRound::getSuccessCount, authoritativeCount)
                    .eq(PetMinigameRound::getId, roundId));
            round.setSuccessCount(authoritativeCount);
        }
        int updated = minigameMapper.update(null, new LambdaUpdateWrapper<PetMinigameRound>()
                .set(PetMinigameRound::getStatus, "SETTLED")
                .eq(PetMinigameRound::getId, roundId)
                .eq(PetMinigameRound::getStatus, "ACTIVE"));
        if (updated == 0 && !"SETTLED".equals(round.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "对局结算冲突");
        }
        boolean valid = round.getSuccessCount() >= MIN_SUCCESS_FOR_REWARD;
        boolean firstSettlement = updated == 1;
        Map<String, Integer> reward = Boolean.TRUE.equals(round.getRewardEligible()) && valid
                ? Map.of("exp", Math.min(8, round.getSuccessCount()), "intimacy", 1,
                "happiness", Math.min(10, round.getSuccessCount()), "coin", 0)
                : Map.of("exp", 0, "intimacy", 0, "happiness", 0, "coin", 0);
        if (firstSettlement && reward.get("exp") > 0) {
            // BE-08：奖励真实落成长（原实现只返回 Map 未落库，"显示已发实际未发"）。
            // 仅 CAS 胜者（首次结算）执行——重复结算返回同一 reward 不再发放（幂等）；
            // 经验走统一成长服务（保留并发升级）；亲密度固定 1（PLAY 口径）；心情直接封顶累加。
            Pet pet = petMapper.selectById(round.getPetId());
            if (pet != null) {
                stateService.grantExp(pet, reward.get("exp"));
                intimacyService.gain(pet, com.cloudmart.pet.enums.PetIntimacySource.PLAY);
                petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                        .setSql("happiness = LEAST(happiness + " + reward.get("happiness") + ", 100)")
                        .eq(Pet::getId, pet.getId()));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", "SETTLED");
        result.put("successCount", round.getSuccessCount());
        result.put("rewardEligible", Boolean.TRUE.equals(round.getRewardEligible()));
        result.put("validCompletion", valid);
        result.put("reward", reward);
        return result;
    }

    /** 对局历史 → 展示投影（N04）：只暴露展示字段，内部字段（序列/流水/归属/配额）不出域 */
    public List<com.cloudmart.pet.vo.PetMinigameRoundVO> history(Long userId, int page, int size) {
        return minigameMapper.selectList(new LambdaQueryWrapper<PetMinigameRound>()
                .eq(PetMinigameRound::getUserId, userId)
                .orderByDesc(PetMinigameRound::getId)
                .last("LIMIT " + Math.min(size, 50) + " OFFSET " + (Math.max(page - 1, 0)) * Math.min(size, 50)))
                .stream()
                .map(round -> new com.cloudmart.pet.vo.PetMinigameRoundVO(
                        round.getId(), round.getGameType(), round.getStatus(), round.getRuleVersion(),
                        round.getStartedAt(), round.getDeadlineAt(), round.getSuccessCount(),
                        round.getRewardEligible()))
                .toList();
    }

    private PetMinigameRound requireActiveRound(Long userId, Long roundId) {
        PetMinigameRound round = minigameMapper.selectById(roundId);
        if (round == null || !round.getUserId().equals(userId) || !"ACTIVE".equals(round.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "对局不存在或已结束");
        }
        return round;
    }


}
