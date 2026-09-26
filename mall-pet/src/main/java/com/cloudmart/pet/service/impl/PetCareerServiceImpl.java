package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.ApplyCareerRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetCareerConfig;
import com.cloudmart.pet.entity.PetCareerProgress;
import com.cloudmart.pet.entity.PetCareerStint;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetCareerConfigMapper;
import com.cloudmart.pet.repository.PetCareerProgressMapper;
import com.cloudmart.pet.repository.PetCareerStintMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetCareerService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetOperationRecoverable;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetCareerItemVO;
import com.cloudmart.pet.vo.PetCareerVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 宠物职业实现（三期 + B01/B02/B03/B10）。
 *
 * <p>与打工的关系：共用 {@code pet_activity}（每用户一次只能做一件事、服务端到点结算、
 * CAS 领奖），但奖励按 {@code pet_career_config} 计算，并额外累计工作次数用于晋升；
 * 职业工作同时计入"打工"类每日任务。</p>
 *
 * <p>B10 规则：入职仅支持第一阶（高阶只能经晋升进入，直接调接口同样拒绝）；晋升统一
 * 校验当前职业、同路线下一阶、开放段工作次数、目标等级/智力与配置有效性；任职历史用
 * {@code pet_career_stint} 独立记录，重新入职不清除原晋升历史。</p>
 *
 * <p>B01 顺序：晋升 = 校验 → 幂等扣款（CAREER_PROMOTE:petId:toCode）→ 本地生效；
 * 领奖 = 本地奖励 → 幂等发薪（CAREER_CLAIM:activityId），结果未知返回"结算中"。</p>
 */
@Service
@Slf4j
public class PetCareerServiceImpl implements PetCareerService, PetOperationRecoverable {

    /** 默认阶位 */
    private static final int DEFAULT_TIER = 1;
    private static final String BIZ_TYPE_PROMOTE = "CAREER_PROMOTE";

    private final PetService petService;
    private final PetStateService stateService;
    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetCareerConfigMapper careerConfigMapper;
    private final PetCareerProgressMapper progressMapper;
    private final PetCareerStintMapper stintMapper;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetDailyQuestService dailyQuestService;
    private final PetIntimacyService intimacyService;
    private final PetProperties properties;
    private final PetOperationService operationService;
    private final PetOutboxService outboxService;
    private final PetClock petClock;

    public PetCareerServiceImpl(PetService petService,
                                PetStateService stateService,
                                PetMapper petMapper,
                                PetActivityMapper activityMapper,
                                PetCareerConfigMapper careerConfigMapper,
                                PetCareerProgressMapper progressMapper,
                                PetCareerStintMapper stintMapper,
                                PetAchievementService achievementService,
                                PetEventProducer eventProducer,
                                PetDailyQuestService dailyQuestService,
                                PetIntimacyService intimacyService,
                                PetProperties properties,
                                PetOperationService operationService,
                                PetOutboxService outboxService,
                                PetClock petClock) {
        this.petService = petService;
        this.stateService = stateService;
        this.petMapper = petMapper;
        this.activityMapper = activityMapper;
        this.careerConfigMapper = careerConfigMapper;
        this.progressMapper = progressMapper;
        this.stintMapper = stintMapper;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.properties = properties;
        this.operationService = operationService;
        this.outboxService = outboxService;
        this.petClock = petClock;
    }

    @Override
    @Transactional
    public PetCareerVO status(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<PetCareerConfig> configs = enabledConfigs();
        Map<String, PetCareerProgress> progressMap = progressMap(pet.getId());
        Map<String, PetCareerConfig> configMap = configs.stream()
                .collect(Collectors.toMap(PetCareerConfig::getCode, Function.identity(), (a, b) -> a));
        PetCareerConfig current = pet.getCareerCode() != null ? configMap.get(pet.getCareerCode()) : null;
        PetCareerProgress currentProgress = pet.getCareerCode() != null ? progressMap.get(pet.getCareerCode()) : null;
        PetCareerStint openStint = current != null ? openStint(pet.getId(), current.getCode()) : null;

        List<PetCareerItemVO> items = configs.stream()
                .sorted(java.util.Comparator.comparing(PetCareerConfig::getCareerLine)
                        .thenComparing(c -> c.getTier() != null ? c.getTier() : DEFAULT_TIER))
                .map(config -> toItemVo(pet, config, configMap, openStintWorkCount(openStint)))
                .toList();

        PetActivityVO active = activeCareerActivity(userId);
        boolean canPromote = current != null && promoteLockReason(pet, current, openStint) == null;
        PetCareerConfig promoteTo = current != null && current.getPromoteToCode() != null
                ? configMap.get(current.getPromoteToCode()) : null;
        List<PetCareerVO.HistoryItem> history = stintHistory(pet.getId()).stream()
                .map(stint -> new PetCareerVO.HistoryItem(stint.getCareerCode(),
                        configMap.containsKey(stint.getCareerCode())
                                ? configMap.get(stint.getCareerCode()).getName() : stint.getCareerCode(),
                        orZero(stint.getWorkCount()), orZero(stint.getTotalCurrency()),
                        stint.getStartedAt(), stint.getEndedAt()))
                .toList();

        return new PetCareerVO(
                pet.getCareerCode(),
                current != null ? current.getName() : null,
                current != null ? current.getCareerLine() : null,
                current != null ? current.getTier() : null,
                current != null ? current.getIcon() : null,
                orZero(openStint != null ? openStint.getWorkCount() : null),
                active,
                canPromote,
                promoteTo != null ? promoteTo.getName() : null,
                current != null ? orZero(current.getPromoteRequiredCount()) : 0,
                current != null ? orZero(current.getPromoteStarCost()) : 0,
                current != null ? promoteLockReason(pet, current, openStint) : null,
                items, history);
    }

    @Override
    @Transactional
    public PetCareerItemVO apply(Long userId, ApplyCareerRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetCareerConfig config = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, request.careerCode())
                .last("LIMIT 1"));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_NOT_FOUND, "这个职业暂时不招人");
        }
        if (config.getCode().equals(pet.getCareerCode())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED, "已经在做这份工作啦");
        }
        // B10：入职仅支持第一阶，高阶只能经晋升进入（修改请求参数直接调用也必须遵守）
        if (orOne(config.getTier()) > 1) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED,
                    "「" + config.getName() + "」是高阶职业，需要先从第一阶晋升");
        }
        // B10：职业工作进行中禁止转职
        requireNoCareerWorkInProgress(pet);
        String lockReason = applyLockReason(pet, config);
        if (lockReason != null) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED, lockReason);
        }
        String previousCode = pet.getCareerCode();
        switchCareer(pet, config.getCode(), "LEFT");
        eventProducer.publish(RocketMQConfig.PET_TAG_CAREER, new PetEventProducer.PetEventMessage(
                "CAREER_JOINED:" + pet.getId() + ":" + config.getCode() + ":" + petClock.nowUtc().toLocalDate(),
                String.valueOf(userId), "PET_CAREER_PROMOTED",
                "我入职啦！",
                pet.getName() + "：主人，我现在是「" + config.getName() + "」啦，明天开始好好上班～",
                String.valueOf(pet.getId()), "PET_CAREER_PROMOTED"));
        log.info("宠物入职: userId={}, petId={}, from={}, to={}", userId, pet.getId(), previousCode, config.getCode());
        Map<String, PetCareerConfig> configMap = enabledConfigs().stream()
                .collect(Collectors.toMap(PetCareerConfig::getCode, Function.identity(), (a, b) -> a));
        return toItemVo(pet, config, configMap, 0);
    }

    @Override
    @Transactional
    public PetActivityVO startWork(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetCareerConfig config = requireCurrentCareer(pet);
        ensureNoBusyActivity(userId);
        requireDailyQuota(userId, config);
        if (pet.getLevel() < orOne(config.getRequiredLevel())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED,
                    "等级达到 Lv." + config.getRequiredLevel() + " 才能做这份工作");
        }
        if (pet.getEnergy() < orZero(config.getEnergyCost())) {
            throw new BusinessException(PetErrorCodes.PET_ENERGY_INSUFFICIENT, "宠物没有力气了，先休息一下吧");
        }
        if (pet.getHunger() < orZero(config.getHungerCost())) {
            throw new BusinessException(PetErrorCodes.PET_HUNGER_TOO_LOW, "肚子太空了干不动活，先喂点东西吧");
        }

        LocalDateTime now = petClock.nowUtc();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("name", config.getName());
        snapshot.put("careerCode", config.getCode());
        snapshot.put("expReward", orZero(config.getExpReward()));
        snapshot.put("currencyReward", orZero(config.getCurrencyReward()));
        snapshot.put("durationSeconds", config.getDurationSeconds() != null ? config.getDurationSeconds() : 0);
        snapshot.put("energyCost", orZero(config.getEnergyCost()));
        snapshot.put("hungerCost", orZero(config.getHungerCost()));

        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(userId);
        activity.setActivityType(PetActivityType.CAREER_WORK.name());
        activity.setConfigId(config.getId());
        activity.setStatus(PetActivityStatus.IN_PROGRESS.name());
        activity.setStartedAt(now);
        activity.setFinishedAt(now.plusSeconds(config.getDurationSeconds() != null ? config.getDurationSeconds() : 0));
        activity.setSnapshot(PetJsonUtils.toJson(snapshot));
        try {
            activityMapper.insert(activity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在忙另一件事啦");
        }
        pet.setEnergy(Math.max(0, pet.getEnergy() - orZero(config.getEnergyCost())));
        pet.setHunger(Math.max(0, pet.getHunger() - orZero(config.getHungerCost())));
        pet.setStatus(PetStatus.WORKING.name());
        petMapperUpdate(pet);
        return toActivityVo(activity, config.getName());
    }

    /** 兼容入口：稳定取本人最新一条可领取 CAREER_WORK */
    @Override
    @Transactional
    public PetActivityVO claimWork(Long userId) {
        return claimByActivity(userId, requireClaimableActivity(userId));
    }

    /** 唯一任务归属领取（B03）：校验活动归属，奖励归 activity.petId，不取当前主宠 */
    @Override
    @Transactional
    public PetActivityVO claimByActivity(Long userId, PetActivity activity) {
        if (!activity.getUserId().equals(userId)
                || !PetActivityType.CAREER_WORK.name().equals(activity.getActivityType())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有这个任务");
        }
        lazyCompleteIfFinished(activity);
        Pet pet = requireActivityPet(activity);
        Map<String, Object> snapshot = PetJsonUtils.parse(activity.getSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        String careerCode = snapshot != null && snapshot.get("careerCode") != null
                ? String.valueOf(snapshot.get("careerCode")) : null;
        PetCareerConfig config = careerConfigMapper.selectById(activity.getConfigId());
        // B09：下架职业中的旧工作仍按快照结算；无快照的存量回退当前配置
        int expReward = snapshot != null ? intOf(snapshot.get("expReward")) : orZero(config != null ? config.getExpReward() : null);
        int currencyReward = snapshot != null ? intOf(snapshot.get("currencyReward")) : orZero(config != null ? config.getCurrencyReward() : null);

        int claimed = claimActivityCas(activity);
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        activity.setClaimedAt(petClock.nowUtc());
        int intelligenceBonus = PetActivityServiceImpl.intelligenceBonusPercent(pet.getIntelligence());
        expReward += Math.round(expReward * intelligenceBonus / 100f);
        currencyReward += Math.round(currencyReward * intelligenceBonus / 100f);
        intimacyService.gain(pet, PetIntimacySource.WORK);
        int levelups = stateService.grantExp(pet, expReward);
        // B01：本地奖励已生效；星光结果未知不回滚
        Integer credited = null;
        if (currencyReward > 0) {
            String operationId = operationService.operationKey("CAREER_CLAIM", activity.getId());
            PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                    operationId, userId, activity.getPetId(), "CAREER_CLAIM", activity.getId(),
                    currencyReward, null);
            if (settlement.isCompleted()) {
                credited = settlement.credited();
            }
        }
        accumulateProgress(pet, careerCode != null ? careerCode
                        : (config != null ? config.getCode() : null), currencyReward);
        activity.setResult(PetJsonUtils.toJson(Map.of(
                "exp", expReward, "currency", currencyReward,
                "actualCurrency", credited == null ? 0 : credited,
                "intelligenceBonus", intelligenceBonus,
                "careerCode", careerCode != null ? careerCode : (config != null ? config.getCode() : ""))));
        activityMapper.updateById(activity);
        dailyQuestService.record(pet, PetQuestType.WORK, 1);
        dailyQuestService.record(pet, PetQuestType.CAREER_WORK, 1);
        achievementService.evaluate(pet, PetAchievementService.Event.WORK_CLAIMED);
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
            String eventId = "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel();
            outboxService.record(eventId, RocketMQConfig.PET_TAG_LEVEL_UP, pet.getUserId(), pet.getId(),
                    new PetEventProducer.PetEventMessage(
                            eventId, String.valueOf(pet.getUserId()), "PET_LEVEL_UP", "宠物升级啦！",
                            pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                            String.valueOf(pet.getId()), "PET_LEVEL_UP"));
        }
        return toActivityVo(activity, snapshot != null && snapshot.get("name") != null
                ? String.valueOf(snapshot.get("name"))
                : (config != null ? config.getName() : null));
    }

    @Override
    @Transactional
    public PetCareerItemVO promote(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetCareerConfig current = requireCurrentCareer(pet);
        PetCareerStint openStint = openStint(pet.getId(), current.getCode());
        String lockReason = promoteLockReason(pet, current, openStint);
        if (lockReason != null) {
            if (current.getPromoteToCode() == null) {
                throw new BusinessException(PetErrorCodes.PET_CAREER_MAX_TIER, "已经是这条路线最高阶啦");
            }
            throw new BusinessException(PetErrorCodes.PET_CAREER_PROMOTE_REQUIRED, lockReason);
        }
        PetCareerConfig target = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, current.getPromoteToCode())
                .last("LIMIT 1"));
        if (target == null || !Boolean.TRUE.equals(target.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_NOT_FOUND, "晋升目标职业暂时不可用");
        }
        requireNoCareerWorkInProgress(pet);

        // B01/B10：幂等扣款先行（重复晋升同键收敛；余额不足/冲突明确失败回滚）
        int cost = orZero(current.getPromoteStarCost());
        if (cost > 0) {
            String operationId = operationService.operationKey(BIZ_TYPE_PROMOTE, pet.getId(), target.getCode());
            String promoteSnapshot = PetJsonUtils.toJson(Map.of(
                    "careerTo", target.getCode(),
                    "careerFrom", current.getCode()));
            PetOperationService.WalletSettlement settlement = operationService.executeSpend(
                    operationId, userId, pet.getId(), BIZ_TYPE_PROMOTE, pet.getId(), cost, promoteSnapshot);
            if (settlement.isUnknown()) {
                throw operationService.settlementPending();
            }
            if (!settlement.isCompleted()) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "星光扣款未完成: " + settlement.lastError());
            }
        }
        switchCareer(pet, target.getCode(), "PROMOTED");
        eventProducer.publish(RocketMQConfig.PET_TAG_CAREER, new PetEventProducer.PetEventMessage(
                "CAREER_PROMOTED:" + pet.getId() + ":" + target.getCode(),
                String.valueOf(userId), "PET_CAREER_PROMOTED",
                "我晋升啦！",
                pet.getName() + "：主人，我晋升成「" + target.getName() + "」啦，以后能赚更多小钱钱！",
                String.valueOf(pet.getId()), "PET_CAREER_PROMOTED"));
        achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        Map<String, PetCareerConfig> configMap = enabledConfigs().stream()
                .collect(Collectors.toMap(PetCareerConfig::getCode, Function.identity(), (a, b) -> a));
        return toItemVo(pet, target, configMap, 0);
    }

    // ---------------- 内部 ----------------

    /** 职业切换（入职/转职/晋升共用）：关闭当前开放段 → 设置新职业 → 新开一段（B10 任职历史） */
    private void switchCareer(Pet pet, String newCareerCode, String endReason) {
        stintMapper.selectList(new LambdaQueryWrapper<PetCareerStint>()
                        .eq(PetCareerStint::getPetId, pet.getId())
                        .isNull(PetCareerStint::getEndedAt))
                .forEach(open -> stintMapper.update(null, new LambdaUpdateWrapper<PetCareerStint>()
                        .set(PetCareerStint::getEndedAt, petClock.nowUtc())
                        .set(PetCareerStint::getEndReason, endReason)
                        .eq(PetCareerStint::getId, open.getId())));
        pet.setCareerCode(newCareerCode);
        pet.setStatus(PetStatus.IDLE.name());
        petMapperUpdate(pet);
        keepProgress(pet, newCareerCode);
        PetCareerStint stint = new PetCareerStint();
        stint.setPetId(pet.getId());
        stint.setUserId(pet.getUserId());
        stint.setCareerCode(newCareerCode);
        stint.setStartedAt(petClock.nowUtc());
        stint.setWorkCount(0);
        stint.setTotalCurrency(0);
        try {
            stintMapper.insert(stint);
        } catch (DuplicateKeyException e) {
            // 同职业重新入职：重开既有开放段（uk 兜底），不新开重复段
            log.debug("任职段已存在，重用开放段: petId={}, code={}", pet.getId(), newCareerCode);
        }
    }

    private void keepProgress(Pet pet, String careerCode) {
        PetCareerProgress existing = progressMapper.selectOne(new LambdaQueryWrapper<PetCareerProgress>()
                .eq(PetCareerProgress::getPetId, pet.getId())
                .eq(PetCareerProgress::getCareerCode, careerCode)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        PetCareerProgress progress = new PetCareerProgress();
        progress.setPetId(pet.getId());
        progress.setUserId(pet.getUserId());
        progress.setCareerCode(careerCode);
        progress.setWorkCount(0);
        progress.setTotalCurrency(0);
        progress.setStartedAt(petClock.nowUtc());
        try {
            progressMapper.insert(progress);
        } catch (DuplicateKeyException e) {
            log.debug("职业进度并发生成，忽略: petId={}, code={}", pet.getId(), careerCode);
        }
    }

    /** 工作奖励累计：终身聚合 + 当前开放段（原子 UPDATE，幂等由领奖 CAS 保证只加一次） */
    private void accumulateProgress(Pet pet, String careerCode, int currencyReward) {
        if (careerCode == null) {
            return;
        }
        progressMapper.update(null, new LambdaUpdateWrapper<PetCareerProgress>()
                .setSql("work_count = work_count + 1")
                .setSql("total_currency = total_currency + " + Math.max(0, currencyReward))
                .eq(PetCareerProgress::getPetId, pet.getId())
                .eq(PetCareerProgress::getCareerCode, careerCode));
        stintMapper.update(null, new LambdaUpdateWrapper<PetCareerStint>()
                .setSql("work_count = work_count + 1")
                .setSql("total_currency = total_currency + " + Math.max(0, currencyReward))
                .eq(PetCareerStint::getPetId, pet.getId())
                .eq(PetCareerStint::getCareerCode, careerCode)
                .isNull(PetCareerStint::getEndedAt));
    }

    private PetCareerStint openStint(Long petId, String careerCode) {
        return stintMapper.selectOne(new LambdaQueryWrapper<PetCareerStint>()
                .eq(PetCareerStint::getPetId, petId)
                .eq(PetCareerStint::getCareerCode, careerCode)
                .isNull(PetCareerStint::getEndedAt)
                .last("LIMIT 1"));
    }

    private List<PetCareerStint> stintHistory(Long petId) {
        return stintMapper.selectList(new LambdaQueryWrapper<PetCareerStint>()
                .eq(PetCareerStint::getPetId, petId)
                .isNotNull(PetCareerStint::getEndedAt)
                .orderByDesc(PetCareerStint::getEndedAt));
    }

    private int openStintWorkCount(PetCareerStint openStint) {
        return openStint != null ? orZero(openStint.getWorkCount()) : 0;
    }

    private List<PetCareerConfig> enabledConfigs() {
        return careerConfigMapper.selectList(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getEnabled, true)
                .orderByAsc(PetCareerConfig::getSort));
    }

    private PetCareerConfig requireCurrentCareer(Pet pet) {
        if (pet.getCareerCode() == null || pet.getCareerCode().isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_REQUIRED, "先找一份工作入职吧");
        }
        PetCareerConfig config = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, pet.getCareerCode())
                .last("LIMIT 1"));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_NOT_FOUND, "当前职业已下架，换一份吧");
        }
        return config;
    }

    private Map<String, PetCareerProgress> progressMap(Long petId) {
        return progressMapper.selectList(new LambdaQueryWrapper<PetCareerProgress>()
                        .eq(PetCareerProgress::getPetId, petId))
                .stream()
                .collect(Collectors.toMap(PetCareerProgress::getCareerCode, Function.identity(), (a, b) -> a));
    }

    private void ensureNoBusyActivity(Long userId) {
        Long busy = activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        if (busy > 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT,
                    "宠物一次只能做一件事，等当前任务结束吧");
        }
    }

    /** B10：职业工作进行中禁止转职/晋升（该宠物维度） */
    private void requireNoCareerWorkInProgress(Pet pet) {
        Long busy = activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getPetId, pet.getId())
                .eq(PetActivity::getActivityType, PetActivityType.CAREER_WORK.name())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        if (busy > 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT,
                    "职业工作进行中，先完成并领取当前工作");
        }
    }

    /** 职业工作每日次数（Redis 快速限频 + 配置上限；数据库权威额度见 B06 统一配额） */
    private void requireDailyQuota(Long userId, PetCareerConfig config) {
        int limit = properties.getCareer().getDailyWorkLimit();
        if (limit <= 0) {
            return;
        }
        try {
            Long used = activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                    .eq(PetActivity::getUserId, userId)
                    .eq(PetActivity::getActivityType, PetActivityType.CAREER_WORK.name())
                    .eq(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                    .ge(PetActivity::getStartedAt, petClock.businessDateStartUtc(petClock.businessDate())));
            if (used != null && used >= limit) {
                throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED,
                        "今天已经工作 " + limit + " 次啦，明天再接着干吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("职业工作次数校验异常，按通过处理（CAS 与快照仍保证一致性）: userId={}", userId, e);
        }
    }

    /** 可领取的职业工作（IN_PROGRESS 已到点则惰性流转为 COMPLETED；稳定序 finished_at desc, id desc） */
    private PetActivity requireClaimableActivity(Long userId) {
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.CAREER_WORK.name())
                .in(PetActivity::getStatus,
                        PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getFinishedAt)
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有可领取的工作");
        }
        lazyCompleteIfFinished(activity);
        return activity;
    }

    private void lazyCompleteIfFinished(PetActivity activity) {
        if (!PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            return;
        }
        if (activity.getFinishedAt().isAfter(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FINISHED, "工作还没做完，再等等吧");
        }
        activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                .eq(PetActivity::getId, activity.getId())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        activity.setStatus(PetActivityStatus.COMPLETED.name());
    }

    private int claimActivityCas(PetActivity activity) {
        return activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                .set(PetActivity::getClaimedAt, petClock.nowUtc())
                .eq(PetActivity::getId, activity.getId())
                .in(PetActivity::getStatus,
                        PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name()));
    }

    private Pet requireActivityPet(PetActivity activity) {
        Pet pet = petMapper.selectById(activity.getPetId());
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "执行任务的宠物不存在");
        }
        return pet;
    }

    private PetActivityVO activeCareerActivity(Long userId) {
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.CAREER_WORK.name())
                .in(PetActivity::getStatus,
                        PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            return null;
        }
        PetCareerConfig config = careerConfigMapper.selectById(activity.getConfigId());
        return toActivityVo(activity, config != null ? config.getName() : null);
    }

    private PetActivityVO toActivityVo(PetActivity activity, String configName) {
        LocalDateTime now = petClock.nowUtc();
        boolean inProgress = PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus());
        long remaining = inProgress
                ? Math.max(0, java.time.Duration.between(now, activity.getFinishedAt()).getSeconds())
                : 0;
        boolean canClaim = PetActivityStatus.COMPLETED.name().equals(activity.getStatus())
                || (inProgress && !activity.getFinishedAt().isAfter(now));
        return new PetActivityVO(activity.getId(), activity.getPetId(), null,
                activity.getActivityType(), activity.getConfigId(),
                configName, activity.getStatus(), activity.getStartedAt(), activity.getFinishedAt(),
                remaining, canClaim, activity.getFinishedAt().plusHours(72),
                activity.getClaimedAt(), activity.getResult());
    }

    private PetCareerItemVO toItemVo(Pet pet, PetCareerConfig config,
                                     Map<String, PetCareerConfig> configMap,
                                     int stintWorkCount) {
        boolean current = config.getCode().equals(pet.getCareerCode());
        String lockReason = applyLockReason(pet, config);
        PetCareerConfig promoteTo = config.getPromoteToCode() != null
                ? configMap.get(config.getPromoteToCode()) : null;
        boolean canPromote = current && config.getPromoteToCode() != null
                && promoteLockReason(pet, config, openStint(pet.getId(), config.getCode())) == null;
        int lifetimeCount = progressLifetimeCount(pet.getId(), config.getCode());
        return new PetCareerItemVO(
                config.getCode(), config.getName(), config.getDescription(), config.getCareerLine(),
                config.getTier() != null ? config.getTier() : DEFAULT_TIER, config.getIcon(),
                config.getRequiredLevel(), config.getRequiredIntelligence(),
                config.getDurationSeconds(), config.getEnergyCost(), config.getHungerCost(),
                config.getExpReward(), config.getCurrencyReward(),
                stintWorkCount,
                current, current || lockReason == null, current ? null : lockReason,
                promoteTo != null ? promoteTo.getName() : null,
                orZero(config.getPromoteRequiredCount()), orZero(config.getPromoteStarCost()),
                lifetimeCount,
                canPromote,
                current ? promoteLockReason(pet, config, openStint(pet.getId(), config.getCode())) : null);
    }

    private int progressLifetimeCount(Long petId, String careerCode) {
        PetCareerProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<PetCareerProgress>()
                .eq(PetCareerProgress::getPetId, petId)
                .eq(PetCareerProgress::getCareerCode, careerCode)
                .last("LIMIT 1"));
        return progress != null ? orZero(progress.getWorkCount()) : 0;
    }

    private String applyLockReason(Pet pet, PetCareerConfig config) {
        if (pet.getLevel() < orOne(config.getRequiredLevel())) {
            return "需要 Lv." + config.getRequiredLevel();
        }
        if (pet.getIntelligence() < orZero(config.getRequiredIntelligence())) {
            return "需要智力 " + config.getRequiredIntelligence();
        }
        return null;
    }

    /** 晋升条件说明（null = 满足）：同路线下一阶 + 开放段次数 + 目标等级/智力（B10 统一口径） */
    private String promoteLockReason(Pet pet, PetCareerConfig config, PetCareerStint openStint) {
        if (config.getPromoteToCode() == null || config.getPromoteToCode().isBlank()) {
            return "已是最高阶";
        }
        int required = orZero(config.getPromoteRequiredCount());
        int done = openStint != null ? orZero(openStint.getWorkCount()) : 0;
        if (done < required) {
            return "还需工作 " + (required - done) + " 次";
        }
        PetCareerConfig target = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, config.getPromoteToCode())
                .last("LIMIT 1"));
        if (target == null) {
            return "晋升目标不存在";
        }
        if (pet.getLevel() < orOne(target.getRequiredLevel())) {
            return "晋升需要 Lv." + target.getRequiredLevel();
        }
        if (pet.getIntelligence() < orZero(target.getRequiredIntelligence())) {
            return "晋升需要智力 " + target.getRequiredIntelligence();
        }
        return null;
    }

    private void petMapperUpdate(Pet pet) {
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "宠物状态被并发修改，请稍后重试");
        }
    }

    private int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static int orOne(Integer value) {
        return value != null ? value : 1;
    }

    // ---------------- B01 恢复回调 ----------------

    @Override
    public String supportedBizType() {
        return BIZ_TYPE_PROMOTE;
    }

    /** 恢复任务回调：钱包已扣款但晋升未生效时按快照幂等补切职业（目标职业已生效=已履约） */
    @Override
    public boolean completePendingOperation(PetOperation operation) {
        Map<String, Object> snapshot = PetJsonUtils.parse(operation.getRewardSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        String careerTo = String.valueOf(snapshot.get("careerTo"));
        Pet pet = petMapper.selectById(operation.getPetId());
        if (pet == null) {
            return false;
        }
        if (careerTo.equals(pet.getCareerCode())) {
            return true;
        }
        switchCareer(pet, careerTo, "PROMOTED");
        return true;
    }
}
