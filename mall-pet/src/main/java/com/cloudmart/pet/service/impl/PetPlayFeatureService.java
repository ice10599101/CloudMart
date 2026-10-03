package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCollectionEntry;
import com.cloudmart.pet.entity.PetCollectionRecord;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetCooperation;
import com.cloudmart.pet.entity.PetCooperationContribution;
import com.cloudmart.pet.entity.PetCustodyRecord;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetMinigameRound;
import com.cloudmart.pet.repository.PetCollectionEntryMapper;
import com.cloudmart.pet.repository.PetCollectionRecordMapper;
import com.cloudmart.pet.repository.PetCooperationContributionMapper;
import com.cloudmart.pet.repository.PetCooperationMapper;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMinigameRoundMapper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 新增玩法后端（N04 接球小游戏 / N05 有限托管 / N06 好友合作周任务 / N07 收藏图鉴）。
 *
 * <p>N04：服务端生成规则快照与随机序列，客户端只提交操作（机会编号+目标+序号），
 * 服务端校验接收时间落窗口；不信客户端分数；每用户每日 5 局有收益且消耗玩耍额度；
 * 训练局 rewardEligible=false 不产生任何养成收益；结算 CAS 幂等。</p>
 *
 * <p>N05：每用户每自然周 1 次（uk）、最长 24h、期间禁止长期活动；照顾效果由
 * 状态读取惰性结算（饱食<30→50 最多 2 次、清洁<30→50 最多 1 次）；不产出任何养成收益。</p>
 *
 * <p>N06：每用户每自然周一支（双向 uk）；贡献按唯一事件去重、每人每天 1 次；
 * 接受时校验剩余业务日 ≥3；退组/解除好友停止累计，名额不恢复。</p>
 *
 * <p>N07：图鉴按用户累计、多宠共享；unlock 幂等（uk user+entry），重复获得仅解锁一次；
 * 未解锁返回线索，隐藏条目不泄漏完整正文。</p>
 */
@Service
@Slf4j
public class PetPlayFeatureService {

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
    /** R12：统一活动互斥（工作/读书/职业/捞瓶/休息/托管跨表排他） */
    private final PetActivityMutex activityMutex;
    /** R12：托管照顾公开事务应用服务 */
    private final PetCustodyCareService custodyCareService;
    private final PetQuotaService quotaService;
    private final PetMinigameRoundMapper minigameMapper;
    /** R11：窗口操作唯一事实（uk roundId+windowIndex，每窗至多一次有效操作） */
    private final com.cloudmart.pet.repository.PetMinigameOperationMapper operationMapper;
    private final PetCustodyRecordMapper custodyMapper;
    private final PetCooperationMapper cooperationMapper;
    private final PetCooperationContributionMapper contributionMapper;
    private final PetCollectionEntryMapper collectionEntryMapper;
    private final PetCollectionRecordMapper collectionRecordMapper;
    private final com.cloudmart.pet.config.PetProperties properties;
    private final com.cloudmart.pet.repository.PetOfflineCursorMapper offlineCursorMapper;
    private final com.cloudmart.pet.repository.PetActivityMapper activityMapper;
    private final com.cloudmart.pet.repository.PetDiaryEntryMapper diaryEntryMapper;
    private final com.cloudmart.pet.repository.PetInventoryMapper inventoryMapper;
    private final PetEconomyService economyService;
    private final com.cloudmart.pet.service.PetUserGuardService guardService;
    private final com.cloudmart.pet.service.PetUserBlockService userBlockService;
    private final com.cloudmart.pet.repository.PetFriendMapper friendMapper;
    private final com.cloudmart.pet.repository.PetRewardClaimMapper rewardClaimMapper;
    private final com.cloudmart.pet.service.impl.PetStateService stateService;
    private final com.cloudmart.pet.service.PetIntimacyService intimacyService;

    public PetPlayFeatureService(PetMapper petMapper, PetClock petClock, PetQuotaService quotaService,
                                 PetActivityMutex activityMutex,
                                 PetCustodyCareService custodyCareService,
                                 PetMinigameRoundMapper minigameMapper,
                                 com.cloudmart.pet.repository.PetMinigameOperationMapper operationMapper,
                                 PetCustodyRecordMapper custodyMapper,
                                 PetCooperationMapper cooperationMapper,
                                 PetCooperationContributionMapper contributionMapper,
                                 PetCollectionEntryMapper collectionEntryMapper,
                                 PetCollectionRecordMapper collectionRecordMapper,
                                 com.cloudmart.pet.config.PetProperties properties,
                                 com.cloudmart.pet.repository.PetOfflineCursorMapper offlineCursorMapper,
                                 com.cloudmart.pet.repository.PetActivityMapper activityMapper,
                                 com.cloudmart.pet.repository.PetDiaryEntryMapper diaryEntryMapper,
                                 com.cloudmart.pet.repository.PetInventoryMapper inventoryMapper,
                                 PetEconomyService economyService,
                                 com.cloudmart.pet.service.PetUserGuardService guardService,
                                 com.cloudmart.pet.service.PetUserBlockService userBlockService,
                                 com.cloudmart.pet.repository.PetFriendMapper friendMapper,
                                 com.cloudmart.pet.repository.PetRewardClaimMapper rewardClaimMapper,
                                 com.cloudmart.pet.service.impl.PetStateService stateService,
                                 com.cloudmart.pet.service.PetIntimacyService intimacyService) {
        this.petMapper = petMapper;
        this.petClock = petClock;
        this.activityMutex = activityMutex;
        this.custodyCareService = custodyCareService;
        this.operationMapper = operationMapper;
        this.quotaService = quotaService;
        this.minigameMapper = minigameMapper;
        this.custodyMapper = custodyMapper;
        this.cooperationMapper = cooperationMapper;
        this.contributionMapper = contributionMapper;
        this.collectionEntryMapper = collectionEntryMapper;
        this.collectionRecordMapper = collectionRecordMapper;
        this.properties = properties;
        this.offlineCursorMapper = offlineCursorMapper;
        this.activityMapper = activityMapper;
        this.diaryEntryMapper = diaryEntryMapper;
        this.inventoryMapper = inventoryMapper;
        this.economyService = economyService;
        this.guardService = guardService;
        this.userBlockService = userBlockService;
        this.friendMapper = friendMapper;
        this.rewardClaimMapper = rewardClaimMapper;
        this.stateService = stateService;
        this.intimacyService = intimacyService;
    }

    // ---------------- N05 离线摘要 ----------------

    /** 首次查询默认回溯窗口（小时）：无游标时按 maxIdleHours 口径回溯 */
    private static final long DIGEST_DEFAULT_LOOKBACK_HOURS = 48;

    /**
     * N05 离线摘要：按上次确认游标聚合离线期间的事实（完成任务/待领取/来访/里程碑），
     * 查询不重发奖励、不推进游标。
     */
    public Map<String, Object> offlineDigest(Long userId) {
        Pet pet = requireActivePet(userId);
        com.cloudmart.pet.entity.PetOfflineCursor cursor = offlineCursorMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                        .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId)
                        .last("LIMIT 1"));
        java.time.LocalDateTime now = petClock.nowUtc();
        java.time.LocalDateTime from = cursor != null ? cursor.getLastConfirmedAt()
                : now.minusHours(DIGEST_DEFAULT_LOOKBACK_HOURS);
        // BE-10：固定摘要上界 throughAt——确认只推进到该点；阅读期间的新事件下轮可见
        java.time.LocalDateTime throughAt = now;

        // 完成=已领取（CLAIMED），待领=完成未领（COMPLETED）——不再共用同一条件（BE-10）
        Long finished = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, "CLAIMED")
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long claimable = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, "COMPLETED")
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long visits = activityMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, com.cloudmart.pet.enums.PetActivityType.VISIT.name())
                .gt(PetActivity::getFinishedAt, from)
                .le(PetActivity::getFinishedAt, throughAt));
        Long milestones = diaryEntryMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetDiaryEntry>()
                .eq(com.cloudmart.pet.entity.PetDiaryEntry::getUserId, userId)
                .gt(com.cloudmart.pet.entity.PetDiaryEntry::getOccurredAt, from)
                .le(com.cloudmart.pet.entity.PetDiaryEntry::getOccurredAt, throughAt));

        Map<String, Object> result = new HashMap<>();
        result.put("offlineHours", java.time.Duration.between(from, throughAt).toHours());
        result.put("from", from);
        result.put("throughAt", throughAt);
        result.put("petState", Map.of("level", pet.getLevel(), "hunger", pet.getHunger(),
                "happiness", pet.getHappiness(), "energy", pet.getEnergy(), "cleanliness", pet.getCleanliness()));
        // §3.5：数量为 JSON number——计数显式转 int，避免 Long 被 ID 定制器连带字符串化
        result.put("finishedTasks", finished == null ? 0 : finished.intValue());
        result.put("claimableTasks", claimable == null ? 0 : claimable.intValue());
        result.put("visits", visits == null ? 0 : visits.intValue());
        result.put("milestones", milestones == null ? 0 : milestones.intValue());
        result.put("hasCursor", cursor != null);
        return result;
    }

    /**
     * N05 确认（BE-10）：只推进到摘要展示时的固定上界 throughAt（不取确认时刻的 now），
     * 且游标不倒退——阅读确认期间新发生的事件不会被越过，下次摘要仍可见。
     */
    @Transactional
    public Map<String, Object> confirmOfflineDigest(Long userId, java.time.LocalDateTime throughAt) {
        requireActivePet(userId);
        java.time.LocalDateTime now = petClock.nowUtc();
        // R40：上界合法性——未来值拒绝（不伪装成 now）；未提供时游标不动
        //（原实现把缺失/未来值改为 now，会跳过尚未展示的事件，T76）
        if (throughAt != null && throughAt.isAfter(now)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "确认上界不能晚于当前时刻");
        }
        java.time.LocalDateTime target = throughAt;
        com.cloudmart.pet.entity.PetOfflineCursor cursor = offlineCursorMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                        .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId)
                        .last("LIMIT 1"));
        if (cursor == null) {
            cursor = new com.cloudmart.pet.entity.PetOfflineCursor();
            cursor.setUserId(userId);
            cursor.setLastConfirmedAt(target);
            try {
                offlineCursorMapper.insert(cursor);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                cursor = offlineCursorMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                                .eq(com.cloudmart.pet.entity.PetOfflineCursor::getUserId, userId));
            }
        }
        // R40：游标原子推进——SQL 条件 MAX（并发确认不倒退，替代读-比-写竞态）
        if (target != null) {
            int advanced = offlineCursorMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.cloudmart.pet.entity.PetOfflineCursor>()
                            .set(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt, target)
                            .eq(com.cloudmart.pet.entity.PetOfflineCursor::getId, cursor.getId())
                            .and(w -> w.isNull(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt)
                                    .or().lt(com.cloudmart.pet.entity.PetOfflineCursor::getLastConfirmedAt, target)));
            cursor.setLastConfirmedAt(advanced > 0 || cursor.getLastConfirmedAt() == null
                    ? target : cursor.getLastConfirmedAt());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("confirmedAt", cursor.getLastConfirmedAt());
        return result;
    }

    // ---------------- N06 个人奖励领取 ----------------

    /** 合作专属装饰编码（达成后每人限领一件；已拥有转固定替代星光 20，走 B01 幂等） */
    private static final String COOP_DECOR_CODE = "cooperation_badge";
    private static final int COOP_ALT_STARLIGHT = 20;

    /**
     * N06 个人领取（B02/BE-01）：pet_reward_claim 唯一事实裁决——同一合作每人至多领取一次，
     * 奖励类型在首次领取时冻结（无装饰则发物品；已拥有则固定替代币），只能其一；
     * 重放（换 Idempotency-Key 再领）返回冻结的原结果，杜绝"先领物再领币"双领；
     * 奖励归属合作参与时绑定的宠物（inviter/invitee petId），切换主宠不改变。
     */
    @Transactional
    public Map<String, Object> claimCooperationReward(Long userId, Long cooperationId) {
        PetCooperation coop = cooperationMapper.selectById(cooperationId);
        if (coop == null || (!userId.equals(coop.getInviterUserId()) && !userId.equals(coop.getInviteeUserId()))) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "合作任务不存在");
        }
        if (!"COMPLETED".equals(coop.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FINISHED, "合作任务尚未达成");
        }
        guardService.lockGuard(userId);
        Long boundPetId = userId.equals(coop.getInviterUserId())
                ? coop.getInviterPetId() : coop.getInviteePetId();
        com.cloudmart.pet.entity.PetRewardClaim claim = new com.cloudmart.pet.entity.PetRewardClaim();
        claim.setUserId(userId);
        claim.setPetId(boundPetId);
        claim.setBizType("COOP_REWARD");
        claim.setBizId(String.valueOf(cooperationId));
        claim.setRewardSlot("MAIN");
        claim.setWalletDomain("PET");
        claim.setStatus("PROCESSING");
        boolean first;
        try {
            rewardClaimMapper.insert(claim);
            first = true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            first = false;
            claim = rewardClaimMapper.selectOne(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetRewardClaim>()
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getUserId, userId)
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getBizType, "COOP_REWARD")
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getBizId, String.valueOf(cooperationId))
                    .eq(com.cloudmart.pet.entity.PetRewardClaim::getRewardSlot, "MAIN"));
            if (claim == null) {
                throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS, "奖励领取处理中，请稍后查询");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("cooperationId", cooperationId);
        if (!first) {
            if (!"COMPLETED".equals(claim.getStatus())) {
                throw new BusinessException(PetErrorCodes.PET_REQUEST_IN_PROGRESS, "奖励领取处理中，请稍后查询");
            }
            result.put("reward", PetJsonUtils.parse(claim.getResultJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    }));
            result.put("duplicate", true);
            return result;
        }

        boolean owned = inventoryMapper.selectCount(
                new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getUserId, userId)
                        .eq(PetInventory::getItemType, "FURNITURE")
                        .eq(PetInventory::getItemCode, COOP_DECOR_CODE)) > 0;
        Map<String, Object> reward;
        if (!owned) {
            com.cloudmart.pet.entity.PetInventory decor = new com.cloudmart.pet.entity.PetInventory();
            decor.setPetId(boundPetId);
            decor.setUserId(userId);
            decor.setItemType("FURNITURE");
            decor.setItemCode(COOP_DECOR_CODE);
            decor.setQuantity(1);
            decor.setEquipped(false);
            decor.setAcquiredAt(petClock.nowUtc());
            try {
                inventoryMapper.insert(decor);
                reward = Map.of("type", "ITEM", "itemCode", COOP_DECOR_CODE);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                reward = Map.of("type", "ITEM", "itemCode", COOP_DECOR_CODE, "duplicate", true);
            }
        } else {
            PetOperationService.WalletSettlement settlement = economyService.earn(
                    userId, boundPetId, "COOP_REWARD_ALT", cooperationId, COOP_ALT_STARLIGHT, null,
                    cooperationId, userId);
            if (!settlement.isCompleted()) {
                throw new BusinessException(PetErrorCodes.PET_SETTLEMENT_PENDING, "替代星光结算中，稍后按原操作查询");
            }
            reward = Map.of("type", "STARLIGHT", "amount", settlement.credited());
        }
        claim.setStatus("COMPLETED");
        claim.setRewardSnapshot(PetJsonUtils.toJson(Map.of("policy", "ITEM_FIRST_ELSE_COIN")));
        claim.setResultJson(PetJsonUtils.toJson(reward));
        rewardClaimMapper.updateById(claim);
        result.put("reward", reward);
        return result;
    }

    private void requireFeature(boolean enabled) {
        if (!enabled) {
            throw new BusinessException(PetErrorCodes.PET_FEATURE_DISABLED, "该功能暂未开放");
        }
    }

    // ---------------- N04 接球小游戏 ----------------

    /** 开始一局（§7.4）：显式 petId 归属校验（不回退主宠）；guard 锁内检查 active 局与额度 */
    @Transactional
    public Map<String, Object> startRound(Long userId, Long petId) {
        requireFeature(properties.getFeatureSwitches().isMinigame());
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

    // ---------------- N05 有限托管 ----------------

    /** 启动托管：每自然周 1 次（uk 幂等）；不收费不自动续。
     * R12：先取用户守卫锁再复验互斥（原实现先检查后加锁，与活动开始并发有竞态窗口）；
     * 锁内经统一互斥 Bean 复验活动与托管，跨表排他成立。 */
    @Transactional
    public Map<String, Object> startCustody(Long userId) {
        requireFeature(properties.getFeatureSwitches().isCustody());
        Pet pet = requireActivePet(userId);
        guardService.lockGuard(userId);
        // BE-09/R12：托管占用统一长期活动名额——锁内重读（打工/读书/捞瓶/休息/进行中托管互斥）
        activityMutex.requireFree(userId);
        LocalDate weekStart = petClock.businessDate().with(DayOfWeek.MONDAY);
        PetCustodyRecord record = new PetCustodyRecord();
        record.setUserId(userId);
        record.setPetId(pet.getId());
        record.setWeekStart(weekStart);
        record.setStatus("ACTIVE");
        record.setStartedAt(petClock.nowUtc());
        record.setEndsAt(petClock.nowUtc().plusHours(24));
        record.setRuleSnapshot(PetJsonUtils.toJson(Map.of(
                "hunger", Map.of("threshold", 30, "restoreTo", 50, "maxTimes", 2),
                "cleanliness", Map.of("threshold", 30, "restoreTo", 50, "maxTimes", 1))));
        record.setCareFeedUsed(0);
        record.setCareCleanUsed(0);
        try {
            custodyMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "本周托管次数已用完或正在托管中");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("custodyId", record.getId());
        result.put("endsAt", record.getEndsAt());
        return result;
    }

    /** 托管状态（惰性应用照顾：按原时间轴分段判定，唯一照顾事件去重） */
    public Map<String, Object> custodyStatus(Long userId) {
        PetCustodyRecord record = custodyMapper.selectOne(new LambdaQueryWrapper<PetCustodyRecord>()
                .eq(PetCustodyRecord::getUserId, userId)
                .eq(PetCustodyRecord::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        Map<String, Object> result = new HashMap<>();
        if (record == null) {
            result.put("active", false);
            result.put("weekUsed", custodyMapper.selectCount(new LambdaQueryWrapper<PetCustodyRecord>()
                    .eq(PetCustodyRecord::getUserId, userId)
                    .eq(PetCustodyRecord::getWeekStart, petClock.businessDate().with(DayOfWeek.MONDAY))) > 0);
            return result;
        }
        // BE-09/R12：到期先结算最后一段照顾再原子结束（T18 最后一段不丢失），结束后不再照顾
        if (record.getEndsAt() != null && record.getEndsAt().isBefore(petClock.nowUtc())) {
            custodyCareService.settleAndEnd(record);
            result.put("active", false);
            result.put("weekUsed", true);
            result.put("nextAvailableAt", petClock.businessDate().with(DayOfWeek.MONDAY).plusWeeks(1));
            return result;
        }
        // R12：照顾进入公开事务应用服务（原 private @Transactional 自调用事务不生效）
        custodyCareService.applyCare(record);
        result.put("active", true);
        result.put("endsAt", record.getEndsAt());
        result.put("careFeedUsed", record.getCareFeedUsed());
        result.put("careCleanUsed", record.getCareCleanUsed());
        return result;
    }

    /** 提前结束（R12：先结算截止当前时刻的照顾再转终态；不退还本周次数） */
    @Transactional
    public void endCustody(Long userId) {
        PetCustodyRecord record = custodyCareService.activeRecord(userId);
        if (record != null) {
            custodyCareService.settleAndEnd(record);
        }
    }

    // ---------------- N06 好友合作周任务 ----------------

    /**
     * 创建邀请（§7.6）：明确指定受邀好友——非本人、有效好友、双方未拉黑；
     * 本周剩余业务日 ≥3；周名额由 uk_cooperation_inviter 兜底。
     */
    @Transactional
    public Map<String, Object> createCooperation(Long userId, Long inviteeUserId) {
        requireFeature(properties.getFeatureSwitches().isCooperation());
        Pet pet = requireActivePet(userId);
        if (inviteeUserId == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "请选择要邀请的好友");
        }
        if (inviteeUserId.equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "不能邀请自己");
        }
        if (userBlockService.isBlockedEitherWay(userId, inviteeUserId)) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED, "无法邀请该用户");
        }
        Long friendRows = friendMapper.selectCount(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetFriend>()
                .and(w -> w.eq(com.cloudmart.pet.entity.PetFriend::getUserId, userId)
                        .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, inviteeUserId)
                        .or()
                        .eq(com.cloudmart.pet.entity.PetFriend::getUserId, inviteeUserId)
                        .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, userId))
                .eq(com.cloudmart.pet.entity.PetFriend::getStatus, "ACTIVE"));
        if (friendRows == null || friendRows == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "只能邀请好友");
        }
        LocalDate today = petClock.businessDate();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        // R35：周末口径统一——周五（剩余 3 个业务日：五/六/日）可组队，周六起新队禁止
        //（原实现创建分支先算 plusDays(2) 又内嵌 >=FRIDAY，放过周六/日）
        if (today.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "本周剩余时间不足，下周一再来组队吧");
        }
        PetCooperation cooperation = new PetCooperation();
        cooperation.setWeekStart(weekStart);
        cooperation.setInviterUserId(userId);
        cooperation.setInviterPetId(pet.getId());
        // R35：明确保存目标受邀人——好友 C 不能接受发给 B 的邀请（T63）
        cooperation.setInviteeUserId(inviteeUserId);
        cooperation.setStatus("INVITED");
        cooperation.setInviteExpiresAt(petClock.nowUtc().plusHours(24));
        cooperation.setContributions(PetJsonUtils.toJson(Map.of("inviter", 0, "invitee", 0)));
        try {
            cooperationMapper.insert(cooperation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "本周已经参加过合作任务啦");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("cooperationId", cooperation.getId());
        result.put("inviteExpiresAt", cooperation.getInviteExpiresAt());
        return result;
    }

    /**
     * 接受邀请（B02/BE-02）：双守卫行锁（userId 升序防死锁）+ 完整资格校验 + CAS 状态迁移。
     * 校验：邀请存在/待接受/未过期、非本人邀请、双方未拉黑、有效好友、本周剩余 >=3 业务日；
     * 并发：CAS（status=INVITED）保证同一邀请只被接受一次（后来者明确冲突，不覆盖先接受者）；
     * 受邀人周名额由 uk_cooperation_invitee(invitee_user_id, week_start) 兜底。
     */
    @Transactional
    public Map<String, Object> acceptCooperation(Long userId, Long cooperationId) {
        PetCooperation cooperation = cooperationMapper.selectById(cooperationId);
        if (cooperation == null || !"INVITED".equals(cooperation.getStatus())
                || cooperation.getInviteExpiresAt().isBefore(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请不存在或已过期");
        }
        if (userId.equals(cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "不能接受自己发起的邀请");
        }
        // R35：仅目标受邀人可接受（原实现任何好友都能接受别人的邀请）
        if (cooperation.getInviteeUserId() == null || !userId.equals(cooperation.getInviteeUserId())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "这份邀请不是发给你的");
        }
        if (userBlockService.isBlockedEitherWay(userId, cooperation.getInviterUserId())) {
            throw new BusinessException(PetErrorCodes.PET_BLOCKED, "无法与该用户组队");
        }
        Long friendRows = friendMapper.selectCount(
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetFriend>()
                        .and(w -> w.eq(com.cloudmart.pet.entity.PetFriend::getUserId, userId)
                                .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, cooperation.getInviterUserId())
                                .or()
                                .eq(com.cloudmart.pet.entity.PetFriend::getUserId, cooperation.getInviterUserId())
                                .eq(com.cloudmart.pet.entity.PetFriend::getFriendUserId, userId))
                        .eq(com.cloudmart.pet.entity.PetFriend::getStatus, "ACTIVE"));
        if (friendRows == null || friendRows == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "只能和好友组队");
        }
        LocalDate today = petClock.businessDate();
        // R35：周末口径与创建一致（周六起不能新组队；跨周邀请已过期）
        if (!today.with(java.time.DayOfWeek.MONDAY).equals(cooperation.getWeekStart())
                || today.getDayOfWeek().getValue() >= java.time.DayOfWeek.SATURDAY.getValue()) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请已过期，下周一再来组队吧");
        }
        guardService.lockGuard(userId);
        guardService.lockGuard(cooperation.getInviterUserId());
        Pet pet = requireActivePet(userId);
        int updated = cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getInviteeUserId, userId)
                .set(PetCooperation::getInviteePetId, pet.getId())
                .set(PetCooperation::getStatus, "ACTIVE")
                .set(PetCooperation::getAcceptedAt, petClock.nowUtc())
                .eq(PetCooperation::getId, cooperationId)
                .eq(PetCooperation::getStatus, "INVITED"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "该邀请已被接受");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ACTIVE");
        return result;
    }

    /**
     * 贡献一次有效照顾（N06/B02-BE-02）：唯一事件去重 + 每人每天 1 次。
     * 计数来源改为贡献事实表 COUNT（并发安全），替代原 contributions JSON 无锁读改写（丢计数）；
     * 用户守卫行锁串行化同用户并发贡献；双方各 3 个不同业务日达成 -> CAS 置 COMPLETED。
     */
    @Transactional
    public void recordContribution(Long userId, String eventId) {
        expireStaleCooperations();
        LocalDate currentWeekStart = petClock.businessDate().with(DayOfWeek.MONDAY);
        // R35：仅本周 ACTIVE 队吸收贡献（原实现无周过滤，旧队跨周继续计数）
        PetCooperation cooperation = cooperationMapper.selectOne(new LambdaQueryWrapper<PetCooperation>()
                .eq(PetCooperation::getStatus, "ACTIVE")
                .eq(PetCooperation::getWeekStart, currentWeekStart)
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .last("LIMIT 1"));
        if (cooperation == null) {
            return;
        }
        // R35：双方守卫按 userId 数值升序加锁——最后一份并发贡献串行提交，
        // 后提交者的 COUNT 可见先提交者的记录，队伍必达 COMPLETED（T65）
        Long inviterId = cooperation.getInviterUserId();
        Long inviteeId = cooperation.getInviteeUserId();
        if (userId < (inviteeId != null ? inviteeId : userId)) {
            guardService.lockGuard(userId);
            guardService.lockGuard(inviterId);
            guardService.lockGuard(inviteeId);
        } else {
            guardService.lockGuard(inviteeId);
            guardService.lockGuard(inviterId);
            guardService.lockGuard(userId);
        }
        LocalDate today = petClock.businessDate();
        PetCooperationContribution contribution = new PetCooperationContribution();
        contribution.setCooperationId(cooperation.getId());
        contribution.setUserId(userId);
        contribution.setEventId(eventId);
        contribution.setBusinessDate(today);
        try {
            contributionMapper.insert(contribution);
        } catch (DuplicateKeyException e) {
            return; // 重复事件/当日已贡献：不增贡献
        }
        Long inviterCount = contributionMapper.selectCount(new LambdaQueryWrapper<PetCooperationContribution>()
                .eq(PetCooperationContribution::getCooperationId, cooperation.getId())
                .eq(PetCooperationContribution::getUserId, cooperation.getInviterUserId()));
        Long inviteeCount = contributionMapper.selectCount(new LambdaQueryWrapper<PetCooperationContribution>()
                .eq(PetCooperationContribution::getCooperationId, cooperation.getId())
                .eq(PetCooperationContribution::getUserId, cooperation.getInviteeUserId()));
        if (inviterCount != null && inviteeCount != null
                && inviterCount >= 3 && inviteeCount >= 3
                && "ACTIVE".equals(cooperation.getStatus())) {
            cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                    .set(PetCooperation::getStatus, "COMPLETED")
                    .eq(PetCooperation::getId, cooperation.getId())
                    .eq(PetCooperation::getStatus, "ACTIVE"));
        }
    }

    /**
     * R35 周截止惰性流转：上周仍未完成的 ACTIVE 队转 EXPIRED（不吸收新周贡献）；
     * 已完成的保留个人领奖（§7.6 状态机 INVITED/ACTIVE/COMPLETED/EXPIRED/ENDED）。
     */
    private void expireStaleCooperations() {
        LocalDate currentWeekStart = petClock.businessDate().with(DayOfWeek.MONDAY);
        cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getStatus, "EXPIRED")
                .set(PetCooperation::getEndedAt, petClock.nowUtc())
                .eq(PetCooperation::getStatus, "ACTIVE")
                .lt(PetCooperation::getWeekStart, currentWeekStart));
        // 过期未接受邀请同样流转（名额不占、可重新邀请）
        cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getStatus, "EXPIRED")
                .set(PetCooperation::getEndedAt, petClock.nowUtc())
                .eq(PetCooperation::getStatus, "INVITED")
                .lt(PetCooperation::getWeekStart, currentWeekStart));
    }

    /** 查询当前/历史合作任务（对方隐私最小化：只返回贡献次数与宠物摘要） */
    public List<PetCooperation> cooperations(Long userId) {
        expireStaleCooperations();
        return cooperationMapper.selectList(new LambdaQueryWrapper<PetCooperation>()
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .orderByDesc(PetCooperation::getId)
                .last("LIMIT 20"));
    }

    /** 退出（停止新增贡献、保留历史；名额不恢复） */
    @Transactional
    public void leaveCooperation(Long userId, Long cooperationId) {
        cooperationMapper.update(null, new LambdaUpdateWrapper<PetCooperation>()
                .set(PetCooperation::getStatus, "ENDED")
                .set(PetCooperation::getEndedAt, petClock.nowUtc())
                .eq(PetCooperation::getId, cooperationId)
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .in(PetCooperation::getStatus, "INVITED", "ACTIVE"));
    }

    // ---------------- N07 收藏图鉴 ----------------

    /** 解锁（多宠重复获得仅一次；uk 幂等）——由商城购买/进化/捞瓶 CAUGHT 等事实路径调用 */
    @Transactional
    public void unlockCollection(Long userId, Long petId, String category, String itemCode, String eventId) {
        String entryCode = category + ":" + itemCode;
        PetCollectionRecord record = new PetCollectionRecord();
        record.setUserId(userId);
        record.setEntryCode(entryCode);
        record.setFirstPetId(petId);
        record.setFirstEventId(eventId);
        record.setAcquiredAt(petClock.nowUtc());
        try {
            collectionRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.debug("图鉴已解锁（幂等跳过）: userId={}, entry={}", userId, entryCode);
        }
    }

    /** 图鉴列表（未解锁返回线索；隐藏条目不泄漏完整正文） */
    public List<Map<String, Object>> collection(Long userId, String category, int page, int size) {
        requireFeature(properties.getFeatureSwitches().isCollection());
        LambdaQueryWrapper<PetCollectionEntry> wrapper = new LambdaQueryWrapper<PetCollectionEntry>()
                .orderByAsc(PetCollectionEntry::getCategory)
                .last("LIMIT " + Math.min(size, 50) + " OFFSET " + Math.max(page - 1, 0) * Math.min(size, 50));
        if (category != null && !category.isBlank()) {
            wrapper.eq(PetCollectionEntry::getCategory, category.toUpperCase());
        }
        List<PetCollectionEntry> entries = collectionEntryMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PetCollectionEntry entry : entries) {
            Map<String, Object> item = new HashMap<>();
            item.put("category", entry.getCategory());
            item.put("itemCode", entry.getItemCode());
            item.put("rarity", entry.getRarity());
            boolean unlocked = collectionRecordMapper.selectCount(new LambdaQueryWrapper<PetCollectionRecord>()
                    .eq(PetCollectionRecord::getUserId, userId)
                    .eq(PetCollectionRecord::getEntryCode, entry.getCategory() + ":" + entry.getItemCode())) > 0;
            item.put("unlocked", unlocked);
            if (unlocked) {
                item.put("resourceKey", entry.getResourceKey());
                item.put("unlockCondition", entry.getUnlockCondition());
            } else {
                item.put("hint", Boolean.TRUE.equals(entry.getHidden())
                        ? "隐藏条目" : entry.getUnlockCondition());
            }
            result.add(item);
        }
        return result;
    }

    /** 收藏统计与进度 */
    public Map<String, Object> collectionStats(Long userId) {
        long total = collectionEntryMapper.selectCount(new LambdaQueryWrapper<>());
        long unlocked = collectionRecordMapper.selectCount(new LambdaQueryWrapper<PetCollectionRecord>()
                .eq(PetCollectionRecord::getUserId, userId));
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("unlocked", unlocked);
        return result;
    }

    private Pet requireActivePet(Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "你还没有宠物");
        }
        return pet;
    }
}
