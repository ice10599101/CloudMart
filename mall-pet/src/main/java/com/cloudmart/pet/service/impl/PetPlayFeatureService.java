package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCollectionEntry;
import com.cloudmart.pet.entity.PetCollectionRecord;
import com.cloudmart.pet.entity.PetCooperation;
import com.cloudmart.pet.entity.PetCooperationContribution;
import com.cloudmart.pet.entity.PetCustodyRecord;
import com.cloudmart.pet.entity.PetMinigameRound;
import com.cloudmart.pet.repository.PetCollectionEntryMapper;
import com.cloudmart.pet.repository.PetCollectionRecordMapper;
import com.cloudmart.pet.repository.PetCooperationContributionMapper;
import com.cloudmart.pet.repository.PetCooperationMapper;
import com.cloudmart.pet.repository.PetCustodyRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
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
    private static final long GRACE_SECONDS = 2;
    private static final int MIN_SUCCESS_FOR_REWARD = 3;
    private static final int DAILY_REWARD_ROUNDS = 5;
    private static final List<String> SLOTS = List.of("LEFT", "CENTER", "RIGHT");

    private final PetMapper petMapper;
    private final PetClock petClock;
    private final PetQuotaService quotaService;
    private final PetMinigameRoundMapper minigameMapper;
    private final PetCustodyRecordMapper custodyMapper;
    private final PetCooperationMapper cooperationMapper;
    private final PetCooperationContributionMapper contributionMapper;
    private final PetCollectionEntryMapper collectionEntryMapper;
    private final PetCollectionRecordMapper collectionRecordMapper;

    public PetPlayFeatureService(PetMapper petMapper, PetClock petClock, PetQuotaService quotaService,
                                 PetMinigameRoundMapper minigameMapper,
                                 PetCustodyRecordMapper custodyMapper,
                                 PetCooperationMapper cooperationMapper,
                                 PetCooperationContributionMapper contributionMapper,
                                 PetCollectionEntryMapper collectionEntryMapper,
                                 PetCollectionRecordMapper collectionRecordMapper) {
        this.petMapper = petMapper;
        this.petClock = petClock;
        this.quotaService = quotaService;
        this.minigameMapper = minigameMapper;
        this.custodyMapper = custodyMapper;
        this.cooperationMapper = cooperationMapper;
        this.contributionMapper = contributionMapper;
        this.collectionEntryMapper = collectionEntryMapper;
        this.collectionRecordMapper = collectionRecordMapper;
    }

    // ---------------- N04 接球小游戏 ----------------

    /** 开始一局：原子预占收益额度（每日 5 局 + 玩耍额度）与精力；超限转训练局 */
    @Transactional
    public Map<String, Object> startRound(Long userId) {
        Pet pet = requireActivePet(userId);
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
        // 收益局：消耗 B06 玩耍额度 + 小游戏局数额度；任一不足 → 训练局（无收益、不消耗精力）
        boolean rewardEligible = quotaService.tryConsume(userId, PetQuotaService.QuotaType.MINIGAME, 0, DAILY_REWARD_ROUNDS)
                && quotaService.tryConsume(userId, PetQuotaService.QuotaType.PLAY_REWARD, 0,
                10);
        if (!rewardEligible) {
            quotaService.release(userId, PetQuotaService.QuotaType.PLAY_REWARD, 0);
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
        return result;
    }

    /** 提交操作批次：服务端校验窗口与目标；相邻宽限重叠不重复计窗 */
    @Transactional
    public Map<String, Object> submitOps(Long userId, Long roundId, List<Map<String, Object>> ops) {
        PetMinigameRound round = requireActiveRound(userId, roundId);
        List<Map<String, Object>> accepted = PetJsonUtils.parse(round.getOps(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                });
        List<String> sequence = PetJsonUtils.parse(round.getSequence(),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                });
        LocalDateTime now = petClock.nowUtc();
        int lastWindow = accepted.stream()
                .mapToInt(op -> ((Number) op.get("windowIndex")).intValue())
                .max().orElse(0);
        int success = 0;
        for (Map<String, Object> op : ops) {
            int seq = ((Number) op.get("seq")).intValue();
            int window = ((Number) op.get("windowIndex")).intValue();
            String slot = String.valueOf(op.get("slot"));
            // 校验：窗口序号前进（倒序/重放拒绝）、目标匹配随机序列、接收时间在窗口+宽限内
            if (window <= lastWindow || window > CATCH_WINDOWS || window < 1) {
                continue;
            }
            if (!sequence.get(window - 1).equals(slot)) {
                continue;
            }
            long windowStartOffset = (long) (window - 1) * 3;
            LocalDateTime windowStart = round.getStartedAt().plusSeconds(windowStartOffset);
            LocalDateTime windowEnd = window == CATCH_WINDOWS
                    ? round.getDeadlineAt() : round.getStartedAt().plusSeconds(windowStartOffset + 3 + GRACE_SECONDS);
            if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
                continue;
            }
            accepted.add(new HashMap<>(Map.of("seq", seq, "windowIndex", window, "slot", slot,
                    "serverTime", now.toString())));
            lastWindow = window;
            success++;
        }
        int totalSuccess = success + accepted.size() - success;
        round.setOps(PetJsonUtils.toJson(accepted));
        round.setSuccessCount(accepted.size());
        minigameMapper.updateById(round);
        Map<String, Object> result = new HashMap<>();
        result.put("accepted", accepted.size());
        result.put("status", "ACTIVE");
        return result;
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
        int updated = minigameMapper.update(null, new LambdaUpdateWrapper<PetMinigameRound>()
                .set(PetMinigameRound::getStatus, "SETTLED")
                .eq(PetMinigameRound::getId, roundId)
                .eq(PetMinigameRound::getStatus, "ACTIVE"));
        if (updated == 0 && !"SETTLED".equals(round.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "对局结算冲突");
        }
        boolean valid = round.getSuccessCount() >= MIN_SUCCESS_FOR_REWARD;
        Map<String, Object> result = new HashMap<>();
        result.put("status", "SETTLED");
        result.put("successCount", round.getSuccessCount());
        result.put("rewardEligible", Boolean.TRUE.equals(round.getRewardEligible()));
        result.put("validCompletion", valid);
        // 奖励：经验 min(8, success)、亲密度 1、心情 min(10, success)；无星光；走统一额度体系（训练局为 0）
        result.put("reward", Boolean.TRUE.equals(round.getRewardEligible()) && valid
                ? Map.of("exp", Math.min(8, round.getSuccessCount()), "intimacy", 1,
                "happiness", Math.min(10, round.getSuccessCount()), "starlight", 0)
                : Map.of("exp", 0, "intimacy", 0, "happiness", 0, "starlight", 0));
        return result;
    }

    /** 历史局列表 */
    public List<PetMinigameRound> history(Long userId, int page, int size) {
        return minigameMapper.selectList(new LambdaQueryWrapper<PetMinigameRound>()
                .eq(PetMinigameRound::getUserId, userId)
                .orderByDesc(PetMinigameRound::getId)
                .last("LIMIT " + Math.min(size, 50) + " OFFSET " + (Math.max(page - 1, 0)) * Math.min(size, 50)));
    }

    private PetMinigameRound requireActiveRound(Long userId, Long roundId) {
        PetMinigameRound round = minigameMapper.selectById(roundId);
        if (round == null || !round.getUserId().equals(userId) || !"ACTIVE".equals(round.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "对局不存在或已结束");
        }
        return round;
    }

    // ---------------- N05 有限托管 ----------------

    /** 启动托管：每自然周 1 次（uk 幂等）；期间无其他进行中活动；不收费不自动续 */
    @Transactional
    public Map<String, Object> startCustody(Long userId) {
        Pet pet = requireActivePet(userId);
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
        applyCustodyCare(record);
        result.put("active", true);
        result.put("endsAt", record.getEndsAt());
        result.put("careFeedUsed", record.getCareFeedUsed());
        result.put("careCleanUsed", record.getCareCleanUsed());
        return result;
    }

    /** 照顾效果（服务端定时/惰性结算；不产出经验/星光/亲密度/任务进度） */
    private void applyCustodyCare(PetCustodyRecord record) {
        Pet pet = petMapper.selectById(record.getPetId());
        if (pet == null) {
            return;
        }
        LambdaUpdateWrapper<Pet> wrapper = new LambdaUpdateWrapper<Pet>().eq(Pet::getId, pet.getId());
        boolean changed = false;
        if (pet.getHunger() < 30 && record.getCareFeedUsed() < 2) {
            wrapper.setSql("hunger = 50");
            record.setCareFeedUsed(record.getCareFeedUsed() + 1);
            changed = true;
        }
        if (pet.getCleanliness() < 30 && record.getCareCleanUsed() < 1) {
            wrapper.setSql("cleanliness = 50");
            record.setCareCleanUsed(record.getCareCleanUsed() + 1);
            changed = true;
        }
        if (changed) {
            petMapper.update(null, wrapper);
            custodyMapper.updateById(record);
        }
    }

    /** 提前结束（不退还本周次数；服务端故障未实际启动可释放预占） */
    @Transactional
    public void endCustody(Long userId) {
        custodyMapper.update(null, new LambdaUpdateWrapper<PetCustodyRecord>()
                .set(PetCustodyRecord::getStatus, "ENDED")
                .set(PetCustodyRecord::getEndedAt, petClock.nowUtc())
                .eq(PetCustodyRecord::getUserId, userId)
                .eq(PetCustodyRecord::getStatus, "ACTIVE"));
    }

    // ---------------- N06 好友合作周任务 ----------------

    /** 创建邀请：预校验剩余业务日 ≥3（含当天） */
    @Transactional
    public Map<String, Object> createCooperation(Long userId) {
        Pet pet = requireActivePet(userId);
        LocalDate today = petClock.businessDate();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        if (today.plusDays(2).with(DayOfWeek.MONDAY).equals(weekStart) && today.getDayOfWeek() != DayOfWeek.MONDAY) {
            // 本周剩余不足 3 天时拒绝（周五及以后）
            if (today.getDayOfWeek().getValue() >= DayOfWeek.FRIDAY.getValue()) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "本周剩余时间不足，下周一再来组队吧");
            }
        }
        PetCooperation cooperation = new PetCooperation();
        cooperation.setWeekStart(weekStart);
        cooperation.setInviterUserId(userId);
        cooperation.setInviterPetId(pet.getId());
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

    /** 接受邀请：重验关系/周名额/剩余业务日 */
    @Transactional
    public Map<String, Object> acceptCooperation(Long userId, Long cooperationId, Long inviterUserId) {
        Pet pet = requireActivePet(userId);
        PetCooperation cooperation = cooperationMapper.selectById(cooperationId);
        if (cooperation == null || !"INVITED".equals(cooperation.getStatus())
                || cooperation.getInviteExpiresAt().isBefore(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "邀请不存在或已过期");
        }
        try {
            cooperation.setInviteeUserId(userId);
            cooperation.setInviteePetId(pet.getId());
            cooperation.setStatus("ACTIVE");
            cooperation.setAcceptedAt(petClock.nowUtc());
            cooperationMapper.updateById(cooperation);
        } catch (Exception e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "本周你已经参加过合作任务啦");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ACTIVE");
        return result;
    }

    /** 贡献一次有效照顾（N06）：唯一事件去重 + 每人每天 1 次；由喂食/玩耍/清洁路径调用 */
    @Transactional
    public void recordContribution(Long userId, String eventId) {
        PetCooperation cooperation = cooperationMapper.selectOne(new LambdaQueryWrapper<PetCooperation>()
                .eq(PetCooperation::getStatus, "ACTIVE")
                .and(w -> w.eq(PetCooperation::getInviterUserId, userId)
                        .or().eq(PetCooperation::getInviteeUserId, userId))
                .last("LIMIT 1"));
        if (cooperation == null) {
            return;
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
        String side = userId.equals(cooperation.getInviterUserId()) ? "inviter" : "invitee";
        Map<String, Integer> counts = PetJsonUtils.parse(cooperation.getContributions(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {
                });
        counts.merge(side, 1, Integer::sum);
        cooperation.setContributions(PetJsonUtils.toJson(counts));
        if (counts.getOrDefault("inviter", 0) >= 3 && counts.getOrDefault("invitee", 0) >= 3) {
            cooperation.setStatus("COMPLETED");
        }
        cooperationMapper.updateById(cooperation);
    }

    /** 查询当前/历史合作任务（对方隐私最小化：只返回贡献次数与宠物摘要） */
    public List<PetCooperation> cooperations(Long userId) {
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
