package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetDailyQuest;
import com.cloudmart.pet.entity.PetDailyQuestConfig;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestStatus;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetDailyQuestConfigMapper;
import com.cloudmart.pet.repository.PetDailyQuestMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetDailyQuestItemVO;
import com.cloudmart.pet.vo.PetDailyQuestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 每日任务实现（三期）。
 *
 * <p>三条不变量：
 * <ol>
 *   <li><b>当日任务行惰性生成</b>：{@code uk_pet_daily_quest} 幂等，先查后插 + 唯一索引兜底并发，
 *       目标值在生成时快照（后台改配置不影响当天已生成任务）。</li>
 *   <li><b>进度累加原子</b>：{@code UPDATE ... SET progress = LEAST(progress + n, target_value)}
 *       一条 SQL 完成，天然避免"读-改-写"丢更新；状态用 CASE 同步翻转（MySQL 左到右求值）。</li>
 *   <li><b>领奖幂等</b>：CAS（COMPLETE → CLAIMED）影响行数 0 即重复领取；
 *       经验本地 + 星光 Feign 同事务（失败整体回滚，AGENTS §17）。</li>
 * </ol>
 * 宝箱用保留任务码 {@code DAILY_CHEST} 记录在同一张表，复用上面的 CAS 幂等机制。</p>
 */
@Service
@Slf4j
public class PetDailyQuestServiceImpl implements PetDailyQuestService {

    /** 全清宝箱的保留任务码（不对应配置表，只在 pet_daily_quest 中留一行审计） */
    static final String CHEST_CODE = "DAILY_CHEST";

    private final PetService petService;
    private final PetStateService stateService;
    private final PetDailyQuestConfigMapper configMapper;
    private final PetDailyQuestMapper questMapper;
    /** R32：任务事件回执（事实去重与补算账） */
    private final com.cloudmart.pet.repository.PetQuestEventReceiptMapper receiptMapper;
    private final WishFeignClient wishFeignClient;
    private final PetEconomyService economyService;
    private final PetClock petClock;
    private final PetIntimacyService intimacyService;
    private final PetAchievementService achievementService;
    private final PetProperties properties;
    /** R13：自代理提供者——批量编排经代理调用单项事务方法（同类 this 调用事务不生效） */
    private final org.springframework.beans.factory.ObjectProvider<PetDailyQuestService> selfProvider;

    private final com.cloudmart.pet.repository.PetMapper petMapper;

    public PetDailyQuestServiceImpl(PetService petService,
                                    PetStateService stateService,
                                    PetDailyQuestConfigMapper configMapper,
                                    PetDailyQuestMapper questMapper,
                                    com.cloudmart.pet.repository.PetQuestEventReceiptMapper receiptMapper,
                                    com.cloudmart.pet.repository.PetMapper petMapper,
                                    WishFeignClient wishFeignClient,
                                    PetEconomyService economyService,
                                    PetClock petClock,
                                    PetIntimacyService intimacyService,
                                    PetAchievementService achievementService,
                                    PetProperties properties,
                                    org.springframework.beans.factory.ObjectProvider<PetDailyQuestService> selfProvider) {
        this.petService = petService;
        this.stateService = stateService;
        this.configMapper = configMapper;
        this.questMapper = questMapper;
        this.receiptMapper = receiptMapper;
        this.petMapper = petMapper;
        this.wishFeignClient = wishFeignClient;
        this.economyService = economyService;
        this.petClock = petClock;
        this.intimacyService = intimacyService;
        this.achievementService = achievementService;
        this.properties = properties;
        this.selfProvider = selfProvider;
    }

    @Override
    @Transactional
    public PetDailyQuestVO list(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        return buildVo(pet, ensureToday(pet));
    }

    @Override
    @Transactional
    public PetDailyQuestItemVO claim(Long userId, String questCode) {
        if (questCode == null || questCode.isBlank() || CHEST_CODE.equals(questCode)) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "这个任务不存在");
        }
        Pet pet = petService.requireOwnedPet(userId);
        ensureToday(pet);
        PetDailyQuest quest = requireQuest(pet, questCode);
        if (!PetQuestStatus.COMPLETE.name().equals(quest.getStatus())) {
            if (PetQuestStatus.CLAIMED.name().equals(quest.getStatus())) {
                throw new BusinessException(PetErrorCodes.PET_QUEST_ALREADY_CLAIMED, "这个任务奖励已经领过啦");
            }
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FINISHED, "任务还没完成，再努努力吧");
        }
        int claimed = questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                .set(PetDailyQuest::getStatus, PetQuestStatus.CLAIMED.name())
                .set(PetDailyQuest::getClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetDailyQuest::getId, quest.getId())
                .eq(PetDailyQuest::getStatus, PetQuestStatus.COMPLETE.name()));
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_ALREADY_CLAIMED, "这个任务奖励已经领过啦");
        }
        PetDailyQuestConfig config = configByCode(questCode);
        // R32：领取按生成时快照入账（运营改配置不改变已生成任务的经济结果，§5.1 不变量 5）；
        // 存量无快照行回退当前配置（迁移兼容，WARN 留痕）
        Map<String, Object> snapshot = parseSnapshot(quest.getRewardSnapshot());
        int expReward;
        int currencyReward;
        if (snapshot != null) {
            expReward = intOf(snapshot.get("expReward"));
            currencyReward = intOf(snapshot.get("currencyReward"));
        } else {
            log.warn("任务行缺奖励快照，回退当前配置（存量兼容）: questId={}, code={}",
                    quest.getId(), questCode);
            expReward = config != null ? orZero(config.getExpReward()) : 0;
            currencyReward = config != null ? orZero(config.getCurrencyReward()) : 0;
        }
        // 亲密度在写库前先叠加（与经验同一次乐观锁写入）
        intimacyService.gain(pet, PetIntimacySource.QUEST);
        int levelups = stateService.grantExp(pet, expReward);
        if (currencyReward > 0) {
            // B01：本地奖励已生效；星光结果未知不回滚，恢复任务按原单收敛
            PetEconomyService.WalletSettlement settlement = economyService.earn(
                    userId, pet.getId(), "QUEST_CLAIM", quest.getId(), currencyReward, null,
                    quest.getId());
            if (!settlement.isCompleted()) {
                log.info("任务奖励星光结算中, questId={}, status={}", quest.getId(), settlement.status());
            }
        }
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        }
        achievementService.evaluate(pet, PetAchievementService.Event.QUEST);
        quest.setStatus(PetQuestStatus.CLAIMED.name());
        return toItemVo(quest, config);
    }

    /**
     * B15/R13 一键领奖：本方法<b>非事务</b>编排——每项经自代理调用 claim（独立事务），
     * 单项回滚不波及其余项（原实现整体一个事务+同类自调用，跨代理异常会把共享事务
     * 标记 rollback-only，全批失败；失败项还伪装成普通 VO，前端无法区分）。
     * 宝箱作为独立动作：普通项全部结束后重新读取已领取状态再评估（不在失败处理中假定成功）。
     */
    @Override
    public com.cloudmart.pet.vo.ClaimAllResult claimAll(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<PetDailyQuest> quests = ensureToday(pet);
        java.util.List<com.cloudmart.pet.vo.QuestClaimResult> results = new java.util.ArrayList<>();
        for (PetDailyQuest quest : quests) {
            if (CHEST_CODE.equals(quest.getQuestCode())
                    || !PetQuestStatus.COMPLETE.name().equals(quest.getStatus())) {
                continue;
            }
            results.add(claimItemIndependently(userId, quest.getQuestCode()));
        }
        // R13：宝箱独立评估（普通项结束后的真实 CLAIMED 状态，失败项不计入门槛）
        com.cloudmart.pet.vo.QuestClaimResult chest;
        try {
            PetDailyQuestVO chestVo = selfProxy().claimChest(userId);
            chest = new com.cloudmart.pet.vo.QuestClaimResult(CHEST_CODE, null,
                    "CLAIMED", chestVo.chestExp(), chestVo.chestCurrency(), null);
        } catch (BusinessException e) {
            chest = new com.cloudmart.pet.vo.QuestClaimResult(CHEST_CODE, null,
                    classify(e.getCode()), null, null, e.getCode());
        }
        return new com.cloudmart.pet.vo.ClaimAllResult(results, chest);
    }

    /** 单项独立事务领取（经代理调用；终态分类，绝不把失败伪装成普通任务 VO） */
    private com.cloudmart.pet.vo.QuestClaimResult claimItemIndependently(Long userId, String questCode) {
        try {
            PetDailyQuestItemVO claimed = selfProxy().claim(userId, questCode);
            return new com.cloudmart.pet.vo.QuestClaimResult(questCode,
                    String.valueOf(claimed.code()), "CLAIMED",
                    claimed.expReward(), claimed.currencyReward(), null);
        } catch (BusinessException e) {
            return new com.cloudmart.pet.vo.QuestClaimResult(questCode, null,
                    classify(e.getCode()), null, null, e.getCode());
        }
    }

    /** 错误码 → 客户端可理解的领取状态（R13 契约：CLAIMED/ALREADY_CLAIMED/NOT_READY/FAILED） */
    private String classify(String errorCode) {
        return switch (errorCode == null ? "" : errorCode) {
            case "PET_QUEST_ALREADY_CLAIMED", "PET_QUEST_CHEST_CLAIMED" -> "ALREADY_CLAIMED";
            case "PET_QUEST_NOT_FINISHED", "PET_QUEST_CHEST_NOT_READY" -> "NOT_READY";
            default -> "FAILED";
        };
    }

    /**
     * 自代理（R13）：claim/claimChest 的 @Transactional 必须经代理才生效，
     * 同类 this 调用会绕过事务边界。ObjectProvider 规避构造期循环依赖。
     */
    private PetDailyQuestService selfProxy() {
        return selfProvider.getObject();
    }

    @Override
    @Transactional
    public PetDailyQuestVO claimChest(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<PetDailyQuest> quests = ensureToday(pet);
        PetProperties.DailyQuest cfg = properties.getDailyQuest();
        List<PetDailyQuest> normalQuests = quests.stream()
                .filter(q -> !CHEST_CODE.equals(q.getQuestCode()))
                .toList();
        PetDailyQuest chest = quests.stream()
                .filter(q -> CHEST_CODE.equals(q.getQuestCode()))
                .findFirst()
                .orElse(null);
        if (chest == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "今日宝箱还没准备好");
        }
        if (PetQuestStatus.CLAIMED.name().equals(chest.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_CLAIMED, "今天的宝箱已经领过啦");
        }
        // R32：宝箱门槛统一计算器——同一 requiredQuestIds 语义（取消项不计门槛、
        // 全部任务被取消/原集合为空时宝箱不可白领）
        if (!chestReady(normalQuests)) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_NOT_READY, "把今天的任务都领完才能开宝箱哦");
        }
        // 宝箱可领：先用 CAS 抢占（COMPLETE → CLAIMED），再发奖（与任务领奖同一幂等口径）
        int marked = questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                .set(PetDailyQuest::getStatus, PetQuestStatus.COMPLETE.name())
                .set(PetDailyQuest::getProgress, normalQuests.size())
                .set(PetDailyQuest::getTargetValue, normalQuests.size())
                .set(PetDailyQuest::getCompletedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetDailyQuest::getId, chest.getId())
                .in(PetDailyQuest::getStatus, PetQuestStatus.IN_PROGRESS.name()));
        if (marked == 0 && !PetQuestStatus.COMPLETE.name().equals(chest.getStatus())) {
            // 并发下另一个请求已领取
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_CLAIMED, "今天的宝箱已经领过啦");
        }
        int claimed = questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                .set(PetDailyQuest::getStatus, PetQuestStatus.CLAIMED.name())
                .set(PetDailyQuest::getClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetDailyQuest::getId, chest.getId())
                .eq(PetDailyQuest::getStatus, PetQuestStatus.COMPLETE.name()));
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_CHEST_CLAIMED, "今天的宝箱已经领过啦");
        }
        // R32：宝箱奖励读生成时快照（存量无快照回退当前配置）
        Map<String, Object> chestSnapshot = parseSnapshot(chest.getRewardSnapshot());
        int chestExp = chestSnapshot != null ? intOf(chestSnapshot.get("chestExp")) : cfg.getChestExp();
        int chestCurrency = chestSnapshot != null ? intOf(chestSnapshot.get("chestCurrency")) : cfg.getChestCurrency();
        int levelups = stateService.grantExp(pet, chestExp);
        if (chestCurrency > 0) {
            PetEconomyService.WalletSettlement settlement = economyService.earn(
                    userId, pet.getId(), "QUEST_CHEST", chest.getId(), chestCurrency, null,
                    userId, chest.getId());
            if (!settlement.isCompleted()) {
                log.info("宝箱奖励星光结算中, chestId={}, status={}", chest.getId(), settlement.status());
            }
        }
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        }
        List<PetDailyQuest> refreshed = questMapper.selectList(todayWrapper(pet));
        return buildVo(pet, refreshed);
    }

    @Override
    public void record(Pet pet, PetQuestType type, int amount) {
        if (pet == null || type == null || amount <= 0) {
            return;
        }
        try {
            ensureToday(pet);
            credit(pet, codesOfType(pet, type), amount, petClock.businessDate());
        } catch (Exception e) {
            // 埋点失败不影响主玩法（进度可丢，玩法不可断）
            log.warn("每日任务埋点失败（忽略）: petId={}, type={}, amount={}", pet.getId(), type, amount, e);
        }
    }

    @Override
    public boolean recordFact(Pet pet, PetQuestType type, String eventId,
                              java.time.LocalDateTime sourceTime, int amount) {
        if (pet == null || type == null || eventId == null || eventId.isBlank()
                || sourceTime == null || amount <= 0) {
            return false;
        }
        try {
            LocalDate factDate = petClock.businessDateOf(sourceTime);
            // 收据先行：uk(quest_type, event_id) 数据库权威去重——与进度累加同事务，回滚一起回滚
            com.cloudmart.pet.entity.PetQuestEventReceipt receipt = new com.cloudmart.pet.entity.PetQuestEventReceipt();
            receipt.setUserId(pet.getUserId());
            receipt.setPetId(pet.getId());
            receipt.setQuestCode(type.name());
            receipt.setEventId(eventId);
            receipt.setAmount(amount);
            receipt.setSourceTime(sourceTime);
            receipt.setBusinessDate(factDate);
            receipt.setStatus("APPLIED");
            try {
                receiptMapper.insert(receipt);
            } catch (DuplicateKeyException duplicate) {
                log.debug("任务事实已消费（幂等跳过）: type={}, eventId={}", type, eventId);
                return false;
            }
            if (factDate.equals(petClock.businessDate())) {
                ensureToday(pet);
                credit(pet, codesOfType(pet, type), amount, factDate);
                return true;
            }
            // 历史事实补算：仅当该日任务行已存在（证明当天参与过）才补记——
            // 不为历史日凭空生成任务行，也不把历史行为加到今天（§13.4）
            int credited = credit(pet, codesOfType(pet, type), amount, factDate);
            if (credited == 0) {
                receipt.setStatus("SKIPPED_STALE");
                receiptMapper.updateById(receipt);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("任务事实埋点失败（忽略）: petId={}, type={}, eventId={}", pet.getId(), type, eventId, e);
            return false;
        }
    }

    @Override
    public com.cloudmart.pet.entity.PetQuestEventReceipt replayReceipt(Long receiptId) {
        com.cloudmart.pet.entity.PetQuestEventReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "回执不存在");
        }
        if (!"SKIPPED_STALE".equals(receipt.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "仅 SKIPPED_STALE 回执可重放（APPLIED 已计入，重放会重复发奖）");
        }
        // CAS SKIPPED_STALE → APPLIED：并发重放单胜
        int updated = receiptMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "APPLIED")
                .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getId, receiptId)
                .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "SKIPPED_STALE"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "回执已被其他操作员处理");
        }
        Pet pet = petMapper.selectById(receipt.getPetId());
        int credited = pet == null ? 0 : credit(pet, codesOfType(pet, PetQuestType.valueOf(receipt.getQuestCode())),
                receipt.getAmount(), receipt.getBusinessDate());
        if (credited == 0) {
            // 该日任务行仍不存在：回滚到 SKIPPED_STALE，重放未生效
            receiptMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                    .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "SKIPPED_STALE")
                    .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getId, receiptId)
                    .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "APPLIED"));
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "该日任务行不存在，无法补算（不允许为历史日凭空生成）");
        }
        return receiptMapper.selectById(receiptId);
    }

    @Override
    public java.util.List<com.cloudmart.pet.entity.PetQuestEventReceipt> receipts(
            Long userId, String questCode, String status, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt> wrapper =
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                        .orderByDesc(com.cloudmart.pet.entity.PetQuestEventReceipt::getId)
                        .last("LIMIT " + pageSize + " OFFSET " + (long) (Math.max(page, 1) - 1) * pageSize);
        if (userId != null) {
            wrapper.eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getUserId, userId);
        }
        if (questCode != null && !questCode.isBlank()) {
            wrapper.eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getQuestCode, questCode.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, status.toUpperCase());
        }
        return receiptMapper.selectList(wrapper);
    }

    /** 当前启用配置中该类型的任务 code 集（与 record/recordFact 同一口径） */
    private List<String> codesOfType(Pet pet, PetQuestType type) {
        return activeConfigs(pet).stream()
                .filter(c -> type.name().equals(c.getQuestType()))
                .map(PetDailyQuestConfig::getCode)
                .toList();
    }

    /** 原子累加 + 状态翻转（progress 先赋值，后续 CASE 读到的是新值）；@return 命中行数 */
    private int credit(Pet pet, List<String> codes, int amount, LocalDate businessDate) {
        int hit = 0;
        for (String code : codes) {
            hit += questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                    .setSql("progress = LEAST(progress + " + amount + ", target_value)")
                    .setSql("status = CASE WHEN status = 'IN_PROGRESS' AND progress >= target_value "
                            + "THEN 'COMPLETE' ELSE status END")
                    .setSql("completed_at = CASE WHEN completed_at IS NULL AND progress >= target_value "
                            + "THEN UTC_TIMESTAMP() ELSE completed_at END")
                    .eq(PetDailyQuest::getPetId, pet.getId())
                    .eq(PetDailyQuest::getQuestDate, businessDate)
                    .eq(PetDailyQuest::getQuestCode, code)
                    .eq(PetDailyQuest::getStatus, PetQuestStatus.IN_PROGRESS.name()));
        }
        return hit;
    }

    // ---------------- 内部 ----------------

    /** 生成/加载当日任务行（含宝箱行），返回当日全部行。
     * R32：生成时冻结奖励快照（运营改配置不改变已生成任务的经济结果）；
     * 接入 cancelDisabledQuests——配置停用/缺失的未完成项显式转 CANCELLED（原方法无调用方）。 */
    private List<PetDailyQuest> ensureToday(Pet pet) {
        LocalDate today = petClock.businessDate();
        List<PetDailyQuest> existing = questMapper.selectList(todayWrapper(pet));
        List<String> existingCodes = existing.stream().map(PetDailyQuest::getQuestCode).toList();
        List<PetDailyQuestConfig> configs = frozenConfigsForToday(pet, today);
        for (PetDailyQuestConfig config : configs) {
            if (existingCodes.contains(config.getCode())) {
                continue;
            }
            PetDailyQuest quest = new PetDailyQuest();
            quest.setPetId(pet.getId());
            quest.setUserId(pet.getUserId());
            quest.setQuestDate(today);
            quest.setQuestCode(config.getCode());
            quest.setProgress(0);
            quest.setTargetValue(config.getTargetValue());
            quest.setStatus(PetQuestStatus.IN_PROGRESS.name());
            quest.setRewardSnapshot(questSnapshot(config));
            try {
                questMapper.insert(quest);
                existing.add(quest);
            } catch (DuplicateKeyException e) {
                // 并发生成：uk_pet_daily_quest 兜底，重读即可
                log.debug("每日任务并发生成，忽略: petId={}, code={}", pet.getId(), config.getCode());
            }
        }
        if (existing.stream().noneMatch(q -> CHEST_CODE.equals(q.getQuestCode()))) {
            PetDailyQuest chest = new PetDailyQuest();
            chest.setPetId(pet.getId());
            chest.setUserId(pet.getUserId());
            chest.setQuestDate(today);
            chest.setQuestCode(CHEST_CODE);
            chest.setProgress(0);
            chest.setTargetValue(configs.size());
            chest.setStatus(PetQuestStatus.IN_PROGRESS.name());
            // R32：宝箱奖励生成时冻结（原实现实时读 properties，运营编辑改变进行中结果）
            chest.setRewardSnapshot(chestSnapshot(properties.getDailyQuest()));
            try {
                questMapper.insert(chest);
                existing.add(chest);
            } catch (DuplicateKeyException e) {
                log.debug("每日宝箱并发生成，忽略: petId={}", pet.getId());
            }
        }
        List<PetDailyQuest> rows = questMapper.selectList(todayWrapper(pet));
        // R32：配置停用/下架 → 未完成项显式 CANCELLED（已完成未领保留快照奖励）
        cancelDisabledQuests(rows,
                configs.stream().map(PetDailyQuestConfig::getCode).collect(java.util.stream.Collectors.toSet()));
        return questMapper.selectList(todayWrapper(pet));
    }

    /** R32 任务奖励快照（§16.2：名称/类型/奖励/引导动作冻结） */
    static String questSnapshot(PetDailyQuestConfig config) {
        return PetJsonUtils.toJson(Map.of(
                "name", config.getName() == null ? "" : config.getName(),
                "description", config.getDescription() == null ? "" : config.getDescription(),
                "icon", config.getIcon() == null ? "" : config.getIcon(),
                "questType", config.getQuestType() == null ? "" : config.getQuestType(),
                "expReward", config.getExpReward() != null ? config.getExpReward() : 0,
                "currencyReward", config.getCurrencyReward() != null ? config.getCurrencyReward() : 0,
                "actionTarget", actionTargetOf(config) == null ? "" : actionTargetOf(config)));
    }

    /** R32 宝箱奖励快照（生成时冻结） */
    static String chestSnapshot(PetProperties.DailyQuest cfg) {
        return PetJsonUtils.toJson(Map.of(
                "chestExp", cfg.getChestExp(),
                "chestCurrency", cfg.getChestCurrency()));
    }

    private LambdaQueryWrapper<PetDailyQuest> todayWrapper(Pet pet) {
        return new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getPetId, pet.getId())
                .eq(PetDailyQuest::getQuestDate, petClock.businessDate())
                .orderByAsc(PetDailyQuest::getId);
    }

    /** 当日启用的任务配置（按宠物等级过滤） */
    private List<PetDailyQuestConfig> activeConfigs(Pet pet) {
        return configMapper.selectList(new LambdaQueryWrapper<PetDailyQuestConfig>()
                        .eq(PetDailyQuestConfig::getEnabled, true)
                        .orderByAsc(PetDailyQuestConfig::getSort))
                .stream()
                .filter(c -> pet.getLevel() >= (c.getRequiredLevel() != null ? c.getRequiredLevel() : 1))
                .toList();
    }

    /**
     * B15：生成当日任务只使用"本业务日开始前已存在"的配置——当日新增/升级解锁的配置
     * 次日生效，冻结任务集合；targetValue 在创建时快照。
     */
    private List<PetDailyQuestConfig> frozenConfigsForToday(Pet pet, LocalDate today) {
        LocalDateTime dayStartUtc = petClock.businessDateStartUtc(today);
        return activeConfigs(pet).stream()
                .filter(c -> c.getCreatedAt() == null || c.getCreatedAt().isBefore(dayStartUtc))
                .toList();
    }

    /** B15：配置停用 → 当日快照显式置 CANCELLED（不静默消失，且不阻挡宝箱） */
    private int cancelDisabledQuests(List<PetDailyQuest> quests, java.util.Set<String> enabledCodes) {
        int cancelled = 0;
        for (PetDailyQuest quest : quests) {
            if (CHEST_CODE.equals(quest.getQuestCode())
                    || PetQuestStatus.IN_PROGRESS.name().equals(quest.getStatus())
                    && !enabledCodes.contains(quest.getQuestCode())) {
                if (!CHEST_CODE.equals(quest.getQuestCode())) {
                    cancelled += questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                            .set(PetDailyQuest::getStatus, PetQuestStatus.CANCELLED.name())
                            .eq(PetDailyQuest::getId, quest.getId())
                            .eq(PetDailyQuest::getStatus, PetQuestStatus.IN_PROGRESS.name()));
                }
            }
        }
        return cancelled;
    }

    /** R32：解析奖励快照（缺失/损坏返回 null，调用方回退当前配置） */
    private static Map<String, Object> parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        return PetJsonUtils.parse(snapshot,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * R32 宝箱门槛统一计算器（与 buildVo 展示同语义）：required = 非取消的普通任务行；
     * required 为空（全部任务取消/无任务）→ 宝箱不可白领；否则 required 全部 CLAIMED 才可开。
     */
    static boolean chestReady(List<PetDailyQuest> normalQuests) {
        List<PetDailyQuest> required = normalQuests.stream()
                .filter(q -> !PetQuestStatus.CANCELLED.name().equals(q.getStatus()))
                .toList();
        return !required.isEmpty()
                && required.stream().allMatch(q -> PetQuestStatus.CLAIMED.name().equals(q.getStatus()));
    }

    private PetDailyQuest requireQuest(Pet pet, String questCode) {
        PetDailyQuest quest = questMapper.selectOne(new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getPetId, pet.getId())
                .eq(PetDailyQuest::getQuestDate, petClock.businessDate())
                .eq(PetDailyQuest::getQuestCode, questCode)
                .last("LIMIT 1"));
        if (quest == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "这个任务今天不在列表里");
        }
        return quest;
    }

    private PetDailyQuestConfig configByCode(String code) {
        return configMapper.selectOne(new LambdaQueryWrapper<PetDailyQuestConfig>()
                .eq(PetDailyQuestConfig::getCode, code)
                .last("LIMIT 1"));
    }

    private PetDailyQuestVO buildVo(Pet pet, List<PetDailyQuest> quests) {
        Map<String, PetDailyQuestConfig> configMap = configMapper.selectList(
                        new LambdaQueryWrapper<PetDailyQuestConfig>().eq(PetDailyQuestConfig::getEnabled, true))
                .stream()
                .collect(Collectors.toMap(PetDailyQuestConfig::getCode, Function.identity(), (a, b) -> a));
        PetProperties.DailyQuest chestCfg = properties.getDailyQuest();
        List<PetDailyQuestItemVO> items = new ArrayList<>();
        int completed = 0;
        int claimed = 0;
        PetDailyQuest chest = null;
        for (PetDailyQuest quest : quests) {
            if (CHEST_CODE.equals(quest.getQuestCode())) {
                chest = quest;
                continue;
            }
            PetDailyQuestConfig config = configMap.get(quest.getQuestCode());
            if (config == null || PetQuestStatus.CANCELLED.name().equals(quest.getStatus())) {
                // B15：配置已下架/任务被取消：显式展示 CANCELLED（不静默消失），不计入宝箱门禁
                items.add(new PetDailyQuestItemVO(quest.getQuestCode(),
                        config != null ? config.getName() : quest.getQuestCode(),
                        config != null ? config.getDescription() : "",
                        config != null ? config.getIcon() : "📌",
                        config != null ? config.getQuestType() : null,
                        quest.getProgress(), quest.getTargetValue(), quest.getStatus(),
                        "已取消", false, null,
                        config != null ? orZero(config.getExpReward()) : 0,
                        config != null ? orZero(config.getCurrencyReward()) : 0));
                continue;
            }
            if (!PetQuestStatus.IN_PROGRESS.name().equals(quest.getStatus())) {
                completed++;
            }
            if (PetQuestStatus.CLAIMED.name().equals(quest.getStatus())) {
                claimed++;
            }
            items.add(toItemVo(quest, config));
        }
        // R32：宝箱资格分母 = required（非取消项）——取消项不算分母，与 claimChest 同一计算器
        long required = items.stream()
                .filter(item -> !"已取消".equals(item.statusLabel()))
                .count();
        boolean allClaimed = required > 0 && claimed >= required;
        boolean chestClaimed = chest != null && PetQuestStatus.CLAIMED.name().equals(chest.getStatus());
        // 宝箱进度实时对齐（列表是只读入口，不做状态翻转，只展示真实进度）
        if (chest != null && !chestClaimed) {
            chest.setProgress(claimed);
        }
        return new PetDailyQuestVO(petClock.businessDate(), items, completed, claimed, items.size(),
                allClaimed && !chestClaimed, chestClaimed,
                chestCfg.getChestExp(), chestCfg.getChestCurrency());
    }

    private PetDailyQuestItemVO toItemVo(PetDailyQuest quest, PetDailyQuestConfig config) {
        String status = quest.getStatus();
        String label = switch (status) {
            case "COMPLETE" -> "可领取";
            case "CLAIMED" -> "已领取";
            default -> "进行中";
        };
        return new PetDailyQuestItemVO(
                quest.getQuestCode(),
                config != null ? config.getName() : quest.getQuestCode(),
                config != null ? config.getDescription() : "",
                config != null ? config.getIcon() : "📌",
                config != null ? config.getQuestType() : null,
                quest.getProgress(),
                quest.getTargetValue(),
                status, label,
                PetQuestStatus.COMPLETE.name().equals(status),
                actionTargetOf(config),
                config != null ? orZero(config.getExpReward()) : 0,
                config != null ? orZero(config.getCurrencyReward()) : 0);
    }

    /** B15：完成动作描述（questType → 客户端动作），服务端权威，不拼接任意 URL */
    static String actionTargetOf(PetDailyQuestConfig config) {
        if (config == null || config.getQuestType() == null) {
            return null;
        }
        return switch (config.getQuestType()) {
            case "FEED" -> "FEED";
            case "PLAY" -> "PLAY";
            case "CLEAN" -> "CLEAN";
            case "WORK" -> "WORK_START";
            case "STUDY" -> "STUDY_START";
            case "BOTTLE" -> "BOTTLE_START";
            case "CAREER_WORK" -> "CAREER_WORK_START";
            case "WALL_MESSAGE" -> "WALL_POST";
            case "REST" -> "REST_START";
            case "DECORATE" -> "HOME_DECORATE";
            case "VISIT" -> "VISIT_NEIGHBOR";
            case "COMPANION" -> "COMPANION_START";
            default -> null;
        };
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
