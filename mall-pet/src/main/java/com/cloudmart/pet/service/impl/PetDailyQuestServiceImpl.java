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
import com.cloudmart.pet.entity.PetDailyQuestSet;
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
    /** PET-10：事实投影自动重试上限（超过后仅管理重放处置） */
    private static final int RECEIPT_MAX_ATTEMPTS = 5;

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
    /** PET-09：任务集实体（每宠每业务日一个，生成后冻结） */
    private final com.cloudmart.pet.repository.PetDailyQuestSetMapper setMapper;

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
                                    org.springframework.beans.factory.ObjectProvider<PetDailyQuestService> selfProvider,
                                    com.cloudmart.pet.repository.PetDailyQuestSetMapper setMapper) {
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
        this.setMapper = setMapper;
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
        return doClaim(pet, quest);
    }

    /** PET-09：领奖主体（claim 与 claimInSet 共用；宠物由调用方按集绑定） */
    private PetDailyQuestItemVO doClaim(Pet pet, PetDailyQuest quest) {
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
        // R32/PET-09：领取按生成时快照入账（运营改配置不改变已生成任务的经济结果，§5.1 不变量 5）；
        // 存量无快照行回退当前配置（迁移兼容，WARN 留痕）
        Map<String, Object> snapshot = parseSnapshot(quest.getRewardSnapshot());
        int expReward;
        int currencyReward;
        if (snapshot != null) {
            expReward = intOf(snapshot.get("expReward"));
            currencyReward = intOf(snapshot.get("currencyReward"));
        } else {
            PetDailyQuestConfig legacyConfig = configByCode(quest.getQuestCode());
            log.warn("任务行缺奖励快照，回退当前配置（存量兼容）: questId={}, code={}",
                    quest.getId(), quest.getQuestCode());
            expReward = legacyConfig != null ? orZero(legacyConfig.getExpReward()) : 0;
            currencyReward = legacyConfig != null ? orZero(legacyConfig.getCurrencyReward()) : 0;
        }
        // 亲密度在写库前先叠加（与经验同一次乐观锁写入）
        intimacyService.gain(pet, PetIntimacySource.QUEST);
        int levelups = stateService.grantExp(pet, expReward);
        if (currencyReward > 0) {
            // B01：本地奖励已生效；星光结果未知不回滚，恢复任务按原单收敛
            PetEconomyService.WalletSettlement settlement = economyService.earn(
                    pet.getUserId(), pet.getId(), "QUEST_CLAIM", quest.getId(), currencyReward, null,
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
        return toItemVo(quest, null);
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
        return doClaimAll(userId, pet, ensureQuestSet(pet, petClock.businessDate()));
    }

    /** PET-09：批领绑定任务集（T17/T19：在途操作绑定原 set/pet，切宠不错对象） */
    @Override
    public com.cloudmart.pet.vo.ClaimAllResult claimAllInSet(Long userId, Long setId) {
        PetDailyQuestSet set = requireOwnedSet(userId, setId);
        Pet pet = petMapper.selectById(set.getPetId());
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "任务集的宠物不存在");
        }
        return doClaimAll(userId, pet, set);
    }

    /**
     * B15/R13/PET-09 批领主体：本方法<b>非事务</b>编排——每项经自代理独立事务领取，
     * 单项回滚不波及其余项；宝箱作为独立动作最后评估（失败项不计入门槛）。
     */
    private com.cloudmart.pet.vo.ClaimAllResult doClaimAll(Long userId, Pet pet, PetDailyQuestSet questSet) {
        List<PetDailyQuest> quests = questMapper.selectList(setWrapper(questSet.getId()));
        java.util.List<com.cloudmart.pet.vo.QuestClaimResult> results = new java.util.ArrayList<>();
        for (PetDailyQuest quest : quests) {
            if (CHEST_CODE.equals(quest.getQuestCode())
                    || !PetQuestStatus.COMPLETE.name().equals(quest.getStatus())) {
                continue;
            }
            results.add(claimItemIndependentlyInSet(userId, questSet.getId(), quest.getQuestCode()));
        }
        // R13：宝箱独立评估（普通项结束后的真实 CLAIMED 状态，失败项不计入门槛）
        com.cloudmart.pet.vo.QuestClaimResult chest;
        try {
            PetDailyQuestVO chestVo = selfProxy().claimChestInSet(userId, questSet.getId());
            chest = new com.cloudmart.pet.vo.QuestClaimResult(CHEST_CODE, null,
                    "CLAIMED", chestVo.chestExp(), chestVo.chestCurrency(), null);
        } catch (BusinessException e) {
            chest = new com.cloudmart.pet.vo.QuestClaimResult(CHEST_CODE, null,
                    classify(e.getCode()), null, null, e.getCode());
        }
        return new com.cloudmart.pet.vo.ClaimAllResult(results, chest);
    }

    /** 单项独立事务领取（经代理按集调用；终态分类，绝不把失败伪装成普通任务 VO） */
    private com.cloudmart.pet.vo.QuestClaimResult claimItemIndependentlyInSet(Long userId, Long setId, String questCode) {
        try {
            PetDailyQuestItemVO claimed = selfProxy().claimInSet(userId, setId, questCode);
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
        return doClaimChest(pet, ensureQuestSet(pet, petClock.businessDate()));
    }

    /** PET-09：按集领取宝箱（T17：切宠/宽限期绑定原集与原宠物） */
    @Override
    @Transactional
    public PetDailyQuestVO claimChestInSet(Long userId, Long setId) {
        PetDailyQuestSet set = requireOwnedSet(userId, setId);
        if (set.getClaimDeadline() != null && petClock.nowUtc().isAfter(set.getClaimDeadline())) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FINISHED, "本期任务奖励已过领取截止");
        }
        Pet pet = petMapper.selectById(set.getPetId());
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "任务集的宠物不存在");
        }
        return doClaimChest(pet, set);
    }

    @Override
    @Transactional
    public Long currentSetId(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        return ensureQuestSet(pet, petClock.businessDate()).getId();
    }

    /** 宝箱领取主体（claimChest 与 claimChestInSet 共用；宠物/集由调用方绑定） */
    private PetDailyQuestVO doClaimChest(Pet pet, PetDailyQuestSet questSet) {
        List<PetDailyQuest> quests = questMapper.selectList(setWrapper(questSet.getId()));
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
                    pet.getUserId(), pet.getId(), "QUEST_CHEST", chest.getId(), chestCurrency, null,
                    pet.getUserId(), chest.getId());
            if (!settlement.isCompleted()) {
                log.info("宝箱奖励星光结算中, chestId={}, status={}", chest.getId(), settlement.status());
            }
        }
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        }
        // PET-09：宝箱领取时间落任务集（集为领取生命周期锚点）；存量行 set_id 为空回退按日查询
        if (chest.getSetId() != null) {
            setMapper.update(null, new LambdaUpdateWrapper<PetDailyQuestSet>()
                    .set(PetDailyQuestSet::getChestClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                    .eq(PetDailyQuestSet::getId, chest.getSetId()));
        }
        List<PetDailyQuest> refreshed = questMapper.selectList(chest.getSetId() != null
                ? setWrapper(chest.getSetId()) : todayWrapper(pet));
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
            // PET-10：收据先行（uk(quest_type, event_id) 数据库权威去重），先以 PENDING 落库——
            // 与主动作同事务提交；投影失败不回滚主动作，落 FAILED 交由调度重试/管理重放恢复
            com.cloudmart.pet.entity.PetQuestEventReceipt receipt = new com.cloudmart.pet.entity.PetQuestEventReceipt();
            receipt.setUserId(pet.getUserId());
            receipt.setPetId(pet.getId());
            receipt.setQuestCode(type.name());
            receipt.setEventId(eventId);
            receipt.setAmount(amount);
            receipt.setSourceTime(sourceTime);
            receipt.setBusinessDate(factDate);
            receipt.setStatus("PENDING");
            receipt.setAttempts(0);
            try {
                receiptMapper.insert(receipt);
            } catch (DuplicateKeyException duplicate) {
                log.debug("任务事实已消费（幂等跳过）: type={}, eventId={}", type, eventId);
                return false;
            }
            // PET-10/T20：投影与回执 APPLIED 同事务提交——credit 失败整事务回滚会连同主动作回滚，
            // 因此投影异常在此转为 FAILED（同事务落库，主动作继续），进度由可靠重试恢复
            try {
                if (factDate.equals(petClock.businessDate())) {
                    ensureToday(pet);
                    credit(pet, codesOfType(pet, type), amount, factDate);
                    receipt.setStatus("APPLIED");
                    receiptMapper.updateById(receipt);
                    return true;
                }
                // 历史事实补算：仅当该日任务行已存在（证明当天参与过）才补记——
                // 不为历史日凭空生成任务行，也不把历史行为加到今天（§13.4）
                int credited = credit(pet, codesOfType(pet, type, factDate), amount, factDate);
                if (credited == 0) {
                    receipt.setStatus("SKIPPED_STALE");
                    receiptMapper.updateById(receipt);
                    return false;
                }
                receipt.setStatus("APPLIED");
                receiptMapper.updateById(receipt);
                return true;
            } catch (Exception projectionError) {
                markReceiptFailed(receipt, projectionError);
                log.warn("任务事实投影失败（转 FAILED 待重试）: petId={}, type={}, eventId={}",
                        pet.getId(), type, eventId, projectionError);
                return false;
            }
        } catch (Exception e) {
            log.warn("任务事实埋点失败（忽略）: petId={}, type={}, eventId={}", pet.getId(), type, eventId, e);
            return false;
        }
    }

    /** PET-10：投影失败 → FAILED + 递增尝试次数 + 指数退避重试时间（同事务落库） */
    private void markReceiptFailed(com.cloudmart.pet.entity.PetQuestEventReceipt receipt, Exception cause) {
        int attempts = receipt.getAttempts() != null ? receipt.getAttempts() + 1 : 1;
        receipt.setStatus("FAILED");
        receipt.setAttempts(attempts);
        // 达到最大尝试次数后停止自动重试（留待管理重放人工处置），不再排期
        receipt.setNextRetryAt(attempts >= RECEIPT_MAX_ATTEMPTS ? null
                : petClock.nowUtc().plusSeconds(Math.min(60L * (1L << Math.min(attempts, 6)), 3600L)));
        receipt.setLastError(truncateError(String.valueOf(cause.getMessage())));
        receiptMapper.updateById(receipt);
    }

    private static String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /**
     * PET-10/T21/T22：管理重放——APPLIED 幂等返回既有结果（不加一次）；SKIPPED_STALE/FAILED
     * 可重放：CAS 抢占 → 进度投影与回执 APPLIED 同事务 → 投影失败回到原状态并排期重试。
     * 双管理员并发重放由 CAS 收敛单胜。
     */
    @Override
    @Transactional
    public com.cloudmart.pet.entity.PetQuestEventReceipt replayReceipt(Long receiptId) {
        com.cloudmart.pet.entity.PetQuestEventReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "回执不存在");
        }
        if ("APPLIED".equals(receipt.getStatus())) {
            // 幂等：已计入的回执重放返回既有结果，不加一次（T21）
            return receipt;
        }
        String fromStatus = receipt.getStatus();
        if (!"SKIPPED_STALE".equals(fromStatus) && !"FAILED".equals(fromStatus)) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "当前状态不可重放: " + fromStatus);
        }
        // CAS {SKIPPED_STALE|FAILED} → APPLIED：并发重放单胜
        int updated = receiptMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "APPLIED")
                .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getLastError, null)
                .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getNextRetryAt, null)
                .setSql("attempts = attempts + 1")
                .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getId, receiptId)
                .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, fromStatus));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "回执已被其他操作员处理");
        }
        receipt.setStatus("APPLIED");
        Pet pet = petMapper.selectById(receipt.getPetId());
        int credited = pet == null ? 0 : credit(pet,
                codesOfType(pet, PetQuestType.valueOf(receipt.getQuestCode()), receipt.getBusinessDate()),
                receipt.getAmount(), receipt.getBusinessDate());
        if (credited == 0) {
            // 该日任务行仍不存在：按原状态回退（重放未生效），不允许为历史日凭空生成
            receiptMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                    .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, fromStatus)
                    .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getId, receiptId)
                    .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "APPLIED"));
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "该日任务行不存在，无法补算（不允许为历史日凭空生成）");
        }
        return receiptMapper.selectById(receiptId);
    }

    /**
     * PET-10：调度重试到期 FAILED 回执（每轮一小批；指数退避排期，达上限留待管理重放）。
     * 单条失败不阻断其余；进度投影与 APPLIED 同事务（调用方事务）。
     *
     * @return 本轮成功转为 APPLIED 的数量
     */
    @Override
    @Transactional
    public int retryFailedReceipts() {
        java.util.List<com.cloudmart.pet.entity.PetQuestEventReceipt> due = receiptMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                        .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "FAILED")
                        .isNotNull(com.cloudmart.pet.entity.PetQuestEventReceipt::getNextRetryAt)
                        .le(com.cloudmart.pet.entity.PetQuestEventReceipt::getNextRetryAt, petClock.nowUtc())
                        .orderByAsc(com.cloudmart.pet.entity.PetQuestEventReceipt::getId)
                        .last("LIMIT 50"));
        int applied = 0;
        for (com.cloudmart.pet.entity.PetQuestEventReceipt receipt : due) {
            try {
                // CAS FAILED → APPLIED（并发调度/管理重放单胜）
                int claimed = receiptMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                        .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "APPLIED")
                        .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getLastError, null)
                        .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getNextRetryAt, null)
                        .setSql("attempts = attempts + 1")
                        .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getId, receipt.getId())
                        .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "FAILED"));
                if (claimed == 0) {
                    continue;
                }
                Pet pet = petMapper.selectById(receipt.getPetId());
                int credited = pet == null ? 0 : credit(pet,
                        codesOfType(pet, PetQuestType.valueOf(receipt.getQuestCode()), receipt.getBusinessDate()),
                        receipt.getAmount(), receipt.getBusinessDate());
                if (credited == 0) {
                    // 该日任务行不存在：保持 FAILED 且不再自动排期（等待管理重放人工处置）
                    receiptMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                            .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "FAILED")
                            .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getNextRetryAt, null)
                            .set(com.cloudmart.pet.entity.PetQuestEventReceipt::getLastError, "重试时该日任务行不存在")
                            .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getId, receipt.getId())
                            .eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, "APPLIED"));
                    continue;
                }
                applied++;
            } catch (Exception e) {
                markReceiptFailed(receipt, e);
                log.warn("任务回执重试失败: receiptId={}, eventId={}", receipt.getId(), receipt.getEventId(), e);
            }
        }
        return applied;
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.cloudmart.pet.entity.PetQuestEventReceipt> receipts(
            Long userId, String questCode, String status, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt> wrapper =
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetQuestEventReceipt>()
                        .orderByDesc(com.cloudmart.pet.entity.PetQuestEventReceipt::getId);
        if (userId != null) {
            wrapper.eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getUserId, userId);
        }
        if (questCode != null && !questCode.isBlank()) {
            wrapper.eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getQuestCode, questCode.strip());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(com.cloudmart.pet.entity.PetQuestEventReceipt::getStatus, status.strip());
        }
        // PET-22/T49：MP 分页插件真实 COUNT——原实现手工 LIMIT/OFFSET 后 Page 未设 total，
        // 页面只能看第 1 页且无法得知剩余量
        return receiptMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        Math.max(page, 1), pageSize), wrapper);
    }

    @Override
    public com.cloudmart.pet.entity.PetDailyQuest cancelQuestInstance(Long petId, LocalDate questDate,
                                                                      String questCode, String operatorName, String reason) {
        if (operatorName == null || operatorName.isBlank() || "unknown".equals(operatorName)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "管理员身份缺失（服务令牌未携带操作者）");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "取消原因必填");
        }
        if (petId == null || questDate == null || questCode == null || questCode.isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "实例定位参数缺失");
        }
        if (questCode.equals(CHEST_CODE)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "全清宝箱不支持实例取消");
        }
        PetDailyQuest existing = questMapper.selectOne(new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getPetId, petId)
                .eq(PetDailyQuest::getQuestDate, questDate)
                .eq(PetDailyQuest::getQuestCode, questCode)
                .last("LIMIT 1"));
        if (existing == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "任务实例不存在");
        }
        if (PetQuestStatus.CLAIMED.name().equals(existing.getStatus())) {
            // 奖励已发出：收回属资金操作，必须走调账补偿审批链，不在本命令范围
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "该实例奖励已领取，收回请走调账补偿链路");
        }
        String trimmedReason = reason.strip();
        int updated = questMapper.update(null, new LambdaUpdateWrapper<PetDailyQuest>()
                .set(PetDailyQuest::getStatus, PetQuestStatus.CANCELLED.name())
                .set(PetDailyQuest::getCancelReason, trimmedReason.length() > 200
                        ? trimmedReason.substring(0, 200) : trimmedReason)
                .set(PetDailyQuest::getCancelledBy, operatorName)
                .eq(PetDailyQuest::getId, existing.getId())
                .in(PetDailyQuest::getStatus, PetQuestStatus.IN_PROGRESS.name(), PetQuestStatus.COMPLETE.name()));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "实例状态已变更，请刷新后重试");
        }
        log.info("任务实例取消（受审计）: petId={}, questDate={}, code={}, operator={}",
                petId, questDate, questCode, operatorName);
        return questMapper.selectById(existing.getId());
    }

    /**
     * PET-09：进度投影按当日任务行自身冻结的 questType（原实现用当前启用配置决定进度，
     * 配置停用/改类型后进度口径漂移）；存量无快照行回退当前配置类型。
     */
    private List<String> codesOfType(Pet pet, PetQuestType type) {
        return codesOfType(pet, type, petClock.businessDate());
    }

    /** PET-09：进度投影按目标业务日的任务行快照（历史补算投影当日行，credit 仍有日期过滤兜底） */
    private List<String> codesOfType(Pet pet, PetQuestType type, LocalDate businessDate) {
        return questMapper.selectList(new LambdaQueryWrapper<PetDailyQuest>()
                        .eq(PetDailyQuest::getPetId, pet.getId())
                        .eq(PetDailyQuest::getQuestDate, businessDate)
                        .ne(PetDailyQuest::getQuestCode, CHEST_CODE))
                .stream()
                .filter(q -> type.name().equals(questTypeOf(q)))
                .map(PetDailyQuest::getQuestCode)
                .toList();
    }

    /** 任务行的统计口径：奖励快照优先，存量无快照回退当前配置（legacy 兼容）；宝箱行不参与进度投影 */
    private String questTypeOf(PetDailyQuest quest) {
        if (CHEST_CODE.equals(quest.getQuestCode())) {
            return "";
        }
        Map<String, Object> snapshot = parseSnapshot(quest.getRewardSnapshot());
        if (snapshot != null && snapshot.get("questType") != null) {
            return String.valueOf(snapshot.get("questType"));
        }
        PetDailyQuestConfig config = configByCode(quest.getQuestCode());
        return config != null && config.getQuestType() != null ? config.getQuestType() : "";
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
        // PET-09：任务集每宠每业务日生成一次（uk 幂等），生成时冻结等级与宝箱奖励——
        // 当日升级/配置编辑不扩大既有集合（T16），任务行经 set_id 绑定到集
        PetDailyQuestSet questSet = ensureQuestSet(pet, today);
        List<PetDailyQuest> existing = questMapper.selectList(setWrapper(questSet));
        List<String> existingCodes = existing.stream().map(PetDailyQuest::getQuestCode).toList();
        List<PetDailyQuestConfig> configs = frozenConfigsForToday(pet, today,
                questSet.getLevelSnapshot() != null ? questSet.getLevelSnapshot() : pet.getLevel());
        for (PetDailyQuestConfig config : configs) {
            if (existingCodes.contains(config.getCode())) {
                continue;
            }
            PetDailyQuest quest = new PetDailyQuest();
            quest.setPetId(pet.getId());
            quest.setUserId(pet.getUserId());
            quest.setQuestDate(today);
            quest.setSetId(questSet.getId());
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
            chest.setSetId(questSet.getId());
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
        List<PetDailyQuest> rows = questMapper.selectList(setWrapper(questSet));
        // R32：配置停用/下架 → 未完成项显式 CANCELLED（已完成未领保留快照奖励）
        cancelDisabledQuests(rows,
                configs.stream().map(PetDailyQuestConfig::getCode).collect(java.util.stream.Collectors.toSet()));
        return questMapper.selectList(setWrapper(questSet));
    }

    /**
     * PET-09：任务集每宠每业务日生成一次（uk(pet_id,business_date) 幂等）。
     * 生成时冻结宠物等级（当日升级不追加高等级任务）、宝箱奖励快照与领取截止
     * （下一业务日结束，24h 宽限）；并发撞 uk 重读既有集。
     */
    private PetDailyQuestSet ensureQuestSet(Pet pet, LocalDate today) {
        PetDailyQuestSet set = setMapper.selectOne(new LambdaQueryWrapper<PetDailyQuestSet>()
                .eq(PetDailyQuestSet::getPetId, pet.getId())
                .eq(PetDailyQuestSet::getBusinessDate, today)
                .last("LIMIT 1"));
        if (set != null) {
            return set;
        }
        PetDailyQuestSet fresh = new PetDailyQuestSet();
        fresh.setUserId(pet.getUserId());
        fresh.setPetId(pet.getId());
        fresh.setBusinessDate(today);
        fresh.setTimezone("Asia/Shanghai");
        fresh.setLevelSnapshot(pet.getLevel() != null ? pet.getLevel() : 1);
        fresh.setGeneratedAt(petClock.nowUtc());
        fresh.setClaimDeadline(petClock.businessDateStartUtc(today.plusDays(1)));
        fresh.setStatus("ACTIVE");
        fresh.setChestSnapshot(chestSnapshot(properties.getDailyQuest()));
        try {
            setMapper.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException e) {
            PetDailyQuestSet existing = setMapper.selectOne(new LambdaQueryWrapper<PetDailyQuestSet>()
                    .eq(PetDailyQuestSet::getPetId, pet.getId())
                    .eq(PetDailyQuestSet::getBusinessDate, today)
                    .last("LIMIT 1"));
            if (existing == null) {
                throw new IllegalStateException("任务集并发创建失败: petId=" + pet.getId());
            }
            return existing;
        }
    }

    /** PET-09：按集查询任务行（列表/进度/领取统一口径，跨日宽限期仍可达） */
    private LambdaQueryWrapper<PetDailyQuest> setWrapper(PetDailyQuestSet questSet) {
        return new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getSetId, questSet.getId())
                .orderByAsc(PetDailyQuest::getId);
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

    /**
     * B15：生成当日任务只使用"本业务日开始前已存在"的配置——当日新增/升级解锁的配置
     * 次日生效，冻结任务集合；targetValue 在创建时快照。
     */
    private List<PetDailyQuestConfig> frozenConfigsForToday(Pet pet, LocalDate today, int levelSnapshot) {
        LocalDateTime dayStartUtc = petClock.businessDateStartUtc(today);
        // PET-09/T16：等级过滤按集生成时的 levelSnapshot——当日升级不追加旧的高等级任务
        return configMapper.selectList(new LambdaQueryWrapper<PetDailyQuestConfig>()
                        .eq(PetDailyQuestConfig::getEnabled, true)
                        .orderByAsc(PetDailyQuestConfig::getSort))
                .stream()
                .filter(c -> levelSnapshot >= (c.getRequiredLevel() != null ? c.getRequiredLevel() : 1))
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

    /** PET-09：按 setId 构造任务行查询（集锚点统一口径） */
    private LambdaQueryWrapper<PetDailyQuest> setWrapper(Long setId) {
        return new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getSetId, setId)
                .orderByAsc(PetDailyQuest::getId);
    }

    // ---------------- PET-09：任务集查询与按集领取 ----------------

    /**
     * 按集领取（PET-09/T17）：setId 为真实任务集实体 ID——归属校验（本人）、
     * 宽限期内（claimDeadline）与任务行归属（set_id 绑定）在同一事务校验；
     * 领取绑定集的原宠物，切换主宠不改变集归属（修复原"领取取当前主宠"跨宠错对象）。
     *
     * @param questIdOrCode 任务实体 ID（数字）或任务 code（旧客户端兼容别名，退役期随 PET-23 收敛）
     */
    @Override
    @Transactional
    public PetDailyQuestItemVO claimInSet(Long userId, Long setId, String questIdOrCode) {
        if (setId == null || questIdOrCode == null || questIdOrCode.isBlank()
                || CHEST_CODE.equals(questIdOrCode)) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "这个任务不存在");
        }
        PetDailyQuestSet set = requireOwnedSet(userId, setId);
        if (set.getClaimDeadline() != null && petClock.nowUtc().isAfter(set.getClaimDeadline())) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FINISHED, "本期任务奖励已过领取截止");
        }
        Pet questPet = petMapper.selectById(set.getPetId());
        if (questPet == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "任务集的宠物不存在");
        }
        PetDailyQuest quest = resolveQuestInSet(set, questIdOrCode);
        return doClaim(questPet, quest);
    }

    /** 集内任务定位：数字按任务实体 ID，否则按 code；必须属于该集 */
    private PetDailyQuest resolveQuestInSet(PetDailyQuestSet set, String questIdOrCode) {
        LambdaQueryWrapper<PetDailyQuest> wrapper = new LambdaQueryWrapper<PetDailyQuest>()
                .eq(PetDailyQuest::getSetId, set.getId());
        try {
            wrapper.eq(PetDailyQuest::getId, Long.parseLong(questIdOrCode.strip()));
        } catch (NumberFormatException codeForm) {
            wrapper.eq(PetDailyQuest::getQuestCode, questIdOrCode.strip());
        }
        PetDailyQuest quest = questMapper.selectOne(wrapper.last("LIMIT 1"));
        if (quest == null) {
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "这个任务不在该任务集里");
        }
        return quest;
    }

    /**
     * PET-09：任务集详情（深链接/刷新/冲突恢复）——完整冻结任务、宝箱、deadline；
     * 宽限期内/历史集均可查询（T15/T25 口径：历史页必须可达）。
     */
    @Override
    @Transactional
    public PetDailyQuestVO questSetDetail(Long userId, Long setId) {
        PetDailyQuestSet set = requireOwnedSet(userId, setId);
        Pet questPet = petMapper.selectById(set.getPetId());
        List<PetDailyQuest> quests = questMapper.selectList(setWrapper(set.getId()));
        return buildVo(questPet != null ? questPet : petWithIdentity(set), quests);
    }

    /**
     * PET-09：本人任务集列表（当前与宽限期内/历史），按业务日倒序。
     * status=ACTIVE→未过领取截止的当前集；EXPIRED→已过截止的历史集；空→全部。
     */
    @Override
    @Transactional
    public java.util.List<com.cloudmart.pet.entity.PetDailyQuestSet> questSets(Long userId, String status) {
        LambdaQueryWrapper<PetDailyQuestSet> wrapper = new LambdaQueryWrapper<PetDailyQuestSet>()
                .eq(PetDailyQuestSet::getUserId, userId)
                .orderByDesc(PetDailyQuestSet::getBusinessDate)
                .last("LIMIT 100");
        if ("ACTIVE".equalsIgnoreCase(status)) {
            wrapper.gt(PetDailyQuestSet::getClaimDeadline, petClock.nowUtc());
        } else if ("EXPIRED".equalsIgnoreCase(status)) {
            wrapper.le(PetDailyQuestSet::getClaimDeadline, petClock.nowUtc());
        }
        return setMapper.selectList(wrapper);
    }

    private PetDailyQuestSet requireOwnedSet(Long userId, Long setId) {
        PetDailyQuestSet set = setMapper.selectById(setId);
        if (set == null || !set.getUserId().equals(userId)) {
            // 归属校验：非本人集合按不存在处理（不泄露他人任务信息）
            throw new BusinessException(PetErrorCodes.PET_QUEST_NOT_FOUND, "任务集不存在");
        }
        return set;
    }

    /** 详情退路：任务集宠物行已被删除时，用集归属字段构造最小展示身份（不产生任何写） */
    private Pet petWithIdentity(PetDailyQuestSet set) {
        Pet pet = new Pet();
        pet.setId(set.getPetId());
        pet.setUserId(set.getUserId());
        pet.setLevel(set.getLevelSnapshot() != null ? set.getLevelSnapshot() : 1);
        return pet;
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

    /**
     * PET-09/T14：列表/详情展示全部读任务行生成时快照（名称/描述/图标/口径/奖励/行动入口）——
     * 模板编辑、停用不再改变已生成任务的展示；存量无快照行回退当前配置（legacy 兼容）。
     */
    private PetDailyQuestVO buildVo(Pet pet, List<PetDailyQuest> quests) {
        List<PetDailyQuestItemVO> items = new ArrayList<>();
        int completed = 0;
        int claimed = 0;
        PetDailyQuest chest = null;
        for (PetDailyQuest quest : quests) {
            if (CHEST_CODE.equals(quest.getQuestCode())) {
                chest = quest;
                continue;
            }
            Map<String, Object> snapshot = parseSnapshot(quest.getRewardSnapshot());
            boolean cancelled = PetQuestStatus.CANCELLED.name().equals(quest.getStatus());
            if (cancelled) {
                // B15：任务被取消：显式展示 CANCELLED（不静默消失），不计入宝箱门禁；
                // 已完成任务的取消保留领取权展示为 CLAIMED/COMPLETE（普通停用不取消已发任务）
                items.add(new PetDailyQuestItemVO(quest.getQuestCode(),
                        snapshotName(snapshot, quest), snapshotText(snapshot, "description"),
                        snapshotIcon(snapshot), snapshotText(snapshot, "questType"),
                        quest.getProgress(), quest.getTargetValue(), quest.getStatus(),
                        "已取消", false, snapshotText(snapshot, "actionTarget"),
                        snapshotInt(snapshot, "expReward"), snapshotInt(snapshot, "currencyReward")));
                continue;
            }
            if (!PetQuestStatus.IN_PROGRESS.name().equals(quest.getStatus())) {
                completed++;
            }
            if (PetQuestStatus.CLAIMED.name().equals(quest.getStatus())) {
                claimed++;
            }
            items.add(toItemVo(quest, snapshot));
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
        // R32/PET-09：宝箱奖励读任务行生成时快照（存量无快照回退当前配置）
        Map<String, Object> chestSnapshot = chest != null ? parseSnapshot(chest.getRewardSnapshot()) : null;
        PetProperties.DailyQuest chestCfg = properties.getDailyQuest();
        return new PetDailyQuestVO(petClock.businessDate(), items, completed, claimed, items.size(),
                allClaimed && !chestClaimed, chestClaimed,
                chestSnapshot != null && chestSnapshot.get("chestExp") != null
                        ? intOf(chestSnapshot.get("chestExp")) : chestCfg.getChestExp(),
                chestSnapshot != null && chestSnapshot.get("chestCurrency") != null
                        ? intOf(chestSnapshot.get("chestCurrency")) : chestCfg.getChestCurrency());
    }

    private PetDailyQuestItemVO toItemVo(PetDailyQuest quest, Map<String, Object> snapshot) {
        String status = quest.getStatus();
        String label = switch (status) {
            case "COMPLETE" -> "可领取";
            case "CLAIMED" -> "已领取";
            default -> "进行中";
        };
        String actionTarget = snapshotText(snapshot, "actionTarget");
        if (actionTarget == null || actionTarget.isBlank()) {
            // 存量无快照行回退当前配置的完成动作（legacy 兼容）
            PetDailyQuestConfig legacy = configByCode(quest.getQuestCode());
            actionTarget = actionTargetOf(legacy);
        }
        return new PetDailyQuestItemVO(
                quest.getQuestCode(),
                snapshotName(snapshot, quest),
                snapshotText(snapshot, "description"),
                snapshotIcon(snapshot),
                snapshot != null && snapshot.get("questType") != null
                        ? String.valueOf(snapshot.get("questType")) : null,
                quest.getProgress(),
                quest.getTargetValue(),
                status, label,
                PetQuestStatus.COMPLETE.name().equals(status),
                actionTarget,
                snapshotInt(snapshot, "expReward"),
                snapshotInt(snapshot, "currencyReward"));
    }

    private static String snapshotName(Map<String, Object> snapshot, PetDailyQuest quest) {
        return snapshot != null && snapshot.get("name") != null
                ? String.valueOf(snapshot.get("name")) : quest.getQuestCode();
    }

    private static String snapshotIcon(Map<String, Object> snapshot) {
        return snapshot != null && snapshot.get("icon") != null
                ? String.valueOf(snapshot.get("icon")) : "📌";
    }

    private static String snapshotText(Map<String, Object> snapshot, String key) {
        return snapshot != null && snapshot.get(key) != null ? String.valueOf(snapshot.get(key)) : "";
    }

    private static int snapshotInt(Map<String, Object> snapshot, String key) {
        return snapshot != null && snapshot.get(key) instanceof Number number ? number.intValue() : 0;
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
