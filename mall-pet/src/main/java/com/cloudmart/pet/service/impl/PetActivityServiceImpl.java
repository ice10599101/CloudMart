package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.StartStudyRequest;
import com.cloudmart.pet.dto.StartWorkRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetJobConfig;
import com.cloudmart.pet.entity.PetStudyConfig;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetJobConfigMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetStudyConfigMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetActivityService;
import com.cloudmart.pet.service.PetBottleFishingService;
import com.cloudmart.pet.service.PetCareerService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetJobVO;
import com.cloudmart.pet.vo.PetStudyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一活动服务实现（打工/读书）。
 *
 * <p>归属与幂等（B03/B09）：领取一律按 activityId + 活动归属用户校验，奖励归
 * {@code activity.petId}（开工宠物），与当前主宠无关——切主宠不影响在途任务。
 * 开始时冻结规则快照（名称/消耗/时长/基础奖励），完成结算只读快照，
 * 运营修改/停用配置不改已开始任务的收益。</p>
 *
 * <p>奖励一致性（B01）：本地奖励（经验/亲密度/智力）先落库，星光经统一操作记录
 * （operationId = CLAIM_WORK/CLAIM_STUDY:activityId）幂等发放；结果未知不回滚本地奖励，
 * 对外返回"奖励结算中"，恢复任务按原单收敛。</p>
 */
@Service
@Slf4j
public class PetActivityServiceImpl implements PetActivityService {

    /** COMPLETED 超过该小时数未领取 → EXPIRED（奖励作废） */
    static final long CLAIM_EXPIRE_HOURS = 72;

    private final PetService petService;
    private final PetStateService stateService;
    private final PetActivityMapper activityMapper;
    private final PetJobConfigMapper jobConfigMapper;
    private final PetStudyConfigMapper studyConfigMapper;
    private final PetMapper petMapper;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetStatsService statsService;
    private final PetDailyQuestService dailyQuestService;
    private final PetIntimacyService intimacyService;
    private final PetOperationService operationService;
    private final PetOutboxService outboxService;
    private final PetClock petClock;
    private final PetCareerService careerService;
    private final PetCompanionFeatureService companionFeatureService;
    private final PetBottleFishingService bottleFishingService;

    public PetActivityServiceImpl(PetService petService,
                                  PetStateService stateService,
                                  PetActivityMapper activityMapper,
                                  PetJobConfigMapper jobConfigMapper,
                                  PetStudyConfigMapper studyConfigMapper,
                                  PetMapper petMapper,
                                  PetAchievementService achievementService,
                                  PetEventProducer eventProducer,
                                  PetStatsService statsService,
                                  PetDailyQuestService dailyQuestService,
                                  PetIntimacyService intimacyService,
                                  PetOperationService operationService,
                                  PetOutboxService outboxService,
                                  PetClock petClock,
                                  PetCareerService careerService,
                                  PetBottleFishingService bottleFishingService,
                                  PetCompanionFeatureService companionFeatureService) {
        this.petService = petService;
        this.stateService = stateService;
        this.activityMapper = activityMapper;
        this.jobConfigMapper = jobConfigMapper;
        this.studyConfigMapper = studyConfigMapper;
        this.petMapper = petMapper;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.statsService = statsService;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.operationService = operationService;
        this.outboxService = outboxService;
        this.petClock = petClock;
        this.careerService = careerService;
        this.bottleFishingService = bottleFishingService;
        this.companionFeatureService = companionFeatureService;
    }

    @Override
    public List<PetJobVO> listJobs(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        return jobConfigMapper.selectList(new LambdaQueryWrapper<PetJobConfig>()
                        .eq(PetJobConfig::getEnabled, true)
                        .orderByAsc(PetJobConfig::getSort))
                .stream()
                .map(job -> new PetJobVO(job.getId(), job.getName(), job.getDescription(),
                        job.getDurationSeconds(), job.getEnergyCost(), job.getHungerCost(),
                        job.getExpReward(), job.getCurrencyReward(), job.getRequiredLevel(),
                        isEligible(pet, job.getRequiredLevel(), job.getEnergyCost(), job.getHungerCost())))
                .toList();
    }

    @Override
    @Transactional
    public PetActivityVO startWork(Long userId, StartWorkRequest request) {
        PetJobConfig job = jobConfigMapper.selectById(request.configId());
        if (job == null || !Boolean.TRUE.equals(job.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_JOB_NOT_FOUND, "这个岗位不存在或已停止招聘");
        }
        return startTimedActivity(userId, PetActivityType.WORK, job.getId(), job.getName(),
                job.getDurationSeconds(), job.getEnergyCost(), job.getHungerCost(), job.getRequiredLevel(),
                Map.of("expReward", job.getExpReward(), "currencyReward", job.getCurrencyReward()));
    }

    @Override
    @Transactional
    public PetActivityVO startStudy(Long userId, StartStudyRequest request) {
        PetStudyConfig study = studyConfigMapper.selectById(request.configId());
        if (study == null || !Boolean.TRUE.equals(study.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_STUDY_NOT_FOUND, "这门课程不存在或已下架");
        }
        return startTimedActivity(userId, PetActivityType.STUDY, study.getId(), study.getName(),
                study.getDurationSeconds(), study.getEnergyCost(), 0, study.getRequiredLevel(),
                Map.of("expReward", study.getExpReward(), "intelligenceReward", study.getIntelligenceReward()));
    }

    @Override
    public List<PetStudyVO> listStudies(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        return studyConfigMapper.selectList(new LambdaQueryWrapper<PetStudyConfig>()
                        .eq(PetStudyConfig::getEnabled, true)
                        .orderByAsc(PetStudyConfig::getSort))
                .stream()
                .map(study -> new PetStudyVO(study.getId(), study.getName(), study.getDescription(),
                        study.getCategory(), study.getDurationSeconds(), study.getEnergyCost(),
                        study.getExpReward(), study.getIntelligenceReward(), study.getRequiredLevel(),
                        isEligible(pet, study.getRequiredLevel(), study.getEnergyCost(), 0)))
                .toList();
    }

    /** 兼容入口：稳定取本人最新一条可领取 WORK（order by finished_at desc, id desc） */
    @Override
    @Transactional
    public PetActivityVO claimWork(Long userId) {
        return claimActivity(userId, requireClaimableActivity(userId, PetActivityType.WORK).getId());
    }

    /** 兼容入口：稳定取本人最新一条可领取 STUDY */
    @Override
    @Transactional
    public PetActivityVO claimStudy(Long userId) {
        return claimActivity(userId, requireClaimableActivity(userId, PetActivityType.STUDY).getId());
    }

    /**
     * 唯一任务归属领取入口（B03）：活动属于当前用户，奖励归 activity.petId。
     * CAREER_WORK/BOTTLE_FISHING 委托对应服务（同一活动、同一 CAS、同一额度体系）。
     */
    @Override
    @Transactional
    public PetActivityVO claimActivity(Long userId, Long activityId) {
        PetActivity activity = activityMapper.selectById(activityId);
        if (activity == null || !activity.getUserId().equals(userId)) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有这个任务");
        }
        return switch (activity.getActivityType()) {
            case "WORK" -> claimWorkActivity(activity);
            case "STUDY" -> claimStudyActivity(activity);
            case "CAREER_WORK" -> careerService.claimByActivity(userId, activity);
            case "BOTTLE_FISHING" -> bottleFishingService.claimByActivity(userId, activity);
            default -> throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND,
                    "该类型活动不支持按 ID 领取");
        };
    }

    @Override
    public List<PetActivityVO> listActivities(Long userId, String status, Long petId, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<PetActivity> wrapper = new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .orderByDesc(PetActivity::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(PetActivity::getStatus, status);
        }
        if (petId != null) {
            wrapper.eq(PetActivity::getPetId, petId);
        }
        Page<PetActivity> result = activityMapper.selectPage(new Page<>(Math.max(page, 1), pageSize), wrapper);
        Map<Long, String> petNames = petNamesByIds(result.getRecords().stream()
                .map(PetActivity::getPetId).distinct().toList());
        return result.getRecords().stream()
                .map(activity -> toVo(activity, petNames.get(activity.getPetId())))
                .toList();
    }

    private Map<Long, String> petNamesByIds(List<Long> petIds) {
        Map<Long, String> names = new HashMap<>();
        if (petIds.isEmpty()) {
            return names;
        }
        petMapper.selectBatchIds(petIds).forEach(pet -> names.put(pet.getId(), pet.getName()));
        return names;
    }

    // ---------------- WORK / STUDY 结算（B09 快照结算 + B01 幂等发薪） ----------------

    private PetActivityVO claimWorkActivity(PetActivity activity) {
        Pet pet = requireActivityPet(activity);
        int claimed = claimActivityCas(activity);
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        activity.setClaimedAt(petClock.nowUtc());

        Map<String, Object> snapshot = rewardSnapshot(activity);
        int expReward = intOf(snapshot.get("expReward"));
        int currencyReward = intOf(snapshot.get("currencyReward"));
        // 智力影响工作收益（原文档 §12）：加成 = min(25%, 智力×0.5%)，加成基于宠物当前智力
        int intelligenceBonus = intelligenceBonusPercent(pet.getIntelligence());
        expReward = expReward + Math.round(expReward * intelligenceBonus / 100f);
        currencyReward = currencyReward + Math.round(currencyReward * intelligenceBonus / 100f);

        intimacyService.gain(pet, PetIntimacySource.WORK);
        int levelups = stateService.grantExp(pet, expReward);
        // B01：本地奖励已生效；星光结果未知不回滚，对外"结算中"，恢复任务按原单收敛
        Integer credited = earnStarlightIdempotent(activity, pet, currencyReward);
        activity.setResult(PetJsonUtils.toJson(Map.of(
                "exp", expReward, "currency", currencyReward, "actualCurrency", credited == null ? 0 : credited,
                "intelligenceBonus", intelligenceBonus,
                "configId", activity.getConfigId() != null ? activity.getConfigId() : 0)));
        activityMapper.updateById(activity);
        dailyQuestService.record(pet, PetQuestType.WORK, 1);

        achievementService.evaluate(pet, PetAchievementService.Event.WORK_CLAIMED);
        companionFeatureService.recordStep(pet.getUserId(), "WORK");
        notifyLevelUp(pet, levelups);
        return toVo(activity, pet.getName());
    }

    private PetActivityVO claimStudyActivity(PetActivity activity) {
        Pet pet = requireActivityPet(activity);
        int claimed = claimActivityCas(activity);
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        activity.setClaimedAt(petClock.nowUtc());

        Map<String, Object> snapshot = rewardSnapshot(activity);
        int expReward = intOf(snapshot.get("expReward"));
        int intelligenceReward = intOf(snapshot.get("intelligenceReward"));
        int intelligenceBonus = intelligenceBonusPercent(pet.getIntelligence());
        expReward = expReward + Math.round(expReward * intelligenceBonus / 100f);
        int skillBonusPercent = (int) Math.round(statsService.studyExpBonus(pet) * 100);
        expReward = expReward + Math.round(expReward * skillBonusPercent / 100f);

        intimacyService.gain(pet, PetIntimacySource.STUDY);
        int levelups = stateService.grantExp(pet, expReward);
        if (intelligenceReward > 0) {
            pet.setIntelligence(Math.min(999, pet.getIntelligence() + intelligenceReward));
            petMapperUpdate(pet);
        }
        activity.setResult(PetJsonUtils.toJson(Map.of(
                "exp", expReward, "intelligence", intelligenceReward, "intelligenceBonus", intelligenceBonus,
                "skillBonus", skillBonusPercent,
                "configId", activity.getConfigId() != null ? activity.getConfigId() : 0)));
        activityMapper.updateById(activity);
        dailyQuestService.record(pet, PetQuestType.STUDY, 1);

        achievementService.evaluate(pet, PetAchievementService.Event.STUDY_CLAIMED);
        notifyLevelUp(pet, levelups);
        return toVo(activity, pet.getName());
    }

    /** 奖励快照（B09）：开始时冻结的配置奖励；存量无快照活动回退当前配置（迁移兼容） */
    private Map<String, Object> rewardSnapshot(PetActivity activity) {
        Map<String, Object> snapshot = PetJsonUtils.parse(activity.getSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        if (snapshot != null) {
            return snapshot;
        }
        log.warn("活动缺少规则快照，回退当前配置结算（存量兼容）, activityId={}, type={}",
                activity.getId(), activity.getActivityType());
        Map<String, Object> rewards = new HashMap<>();
        if ("WORK".equals(activity.getActivityType()) && activity.getConfigId() != null) {
            PetJobConfig job = jobConfigMapper.selectById(activity.getConfigId());
            if (job != null) {
                rewards.put("expReward", job.getExpReward());
                rewards.put("currencyReward", job.getCurrencyReward());
            }
        } else if ("STUDY".equals(activity.getActivityType()) && activity.getConfigId() != null) {
            PetStudyConfig study = studyConfigMapper.selectById(activity.getConfigId());
            if (study != null) {
                rewards.put("expReward", study.getExpReward());
                rewards.put("intelligenceReward", study.getIntelligenceReward());
            }
        }
        return rewards;
    }

    /** 幂等发薪：返回钱包实际到账（未知/失败返回 null，结果 JSON 记 actualCurrency=0 + 结算中状态） */
    private Integer earnStarlightIdempotent(PetActivity activity, Pet pet, int amount) {
        if (amount <= 0) {
            return 0;
        }
        String bizType = "WORK".equals(activity.getActivityType()) ? "CLAIM_WORK" : "CLAIM_STUDY";
        String operationId = operationService.operationKey(bizType, activity.getId());
        PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                operationId, activity.getUserId(), activity.getPetId(), bizType, activity.getId(),
                amount, null);
        if (settlement.isCompleted()) {
            return settlement.credited();
        }
        log.info("活动奖励星光结算中, activityId={}, operationId={}, status={}",
                activity.getId(), operationId, settlement.status());
        return null;
    }

    // ---------------- 内部共用 ----------------

    private PetActivityVO startTimedActivity(Long userId, PetActivityType type, Long configId, String configName,
                                             Integer durationSeconds, Integer energyCost, Integer hungerCost,
                                             Integer requiredLevel, Map<String, Object> rewardRules) {
        Pet pet = petService.requireOwnedPet(userId);
        ensureNoBusyActivity(userId);

        if (!isEligible(pet, requiredLevel, energyCost, hungerCost)) {
            if (pet.getLevel() < requiredLevel) {
                throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED,
                        "等级达到 Lv." + requiredLevel + " 才能解锁哦");
            }
            if (pet.getEnergy() < energyCost) {
                throw new BusinessException(PetErrorCodes.PET_ENERGY_INSUFFICIENT, "宠物没有力气了，先休息一下吧");
            }
            throw new BusinessException(PetErrorCodes.PET_HUNGER_TOO_LOW, "肚子太空了干不动活，先喂点东西吧");
        }

        LocalDateTime now = petClock.nowUtc();
        Map<String, Object> snapshot = new HashMap<>(rewardRules);
        snapshot.put("name", configName);
        snapshot.put("durationSeconds", durationSeconds != null ? durationSeconds : 0);
        snapshot.put("energyCost", energyCost != null ? energyCost : 0);
        snapshot.put("hungerCost", hungerCost != null ? hungerCost : 0);

        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(userId);
        activity.setActivityType(type.name());
        activity.setConfigId(configId);
        activity.setStatus(PetActivityStatus.IN_PROGRESS.name());
        activity.setStartedAt(now);
        activity.setFinishedAt(now.plusSeconds(durationSeconds != null ? durationSeconds : 0));
        activity.setSnapshot(PetJsonUtils.toJson(snapshot));
        try {
            activityMapper.insert(activity);
        } catch (DuplicateKeyException e) {
            // 并发开工：uk_activity_user_active_v2（每用户一条进行中，跨类型互斥）兜底
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在忙另一件事啦");
        }

        // 立即扣消耗（不欠账）：精力/饥饿同一次乐观锁写入
        pet.setEnergy(Math.max(0, pet.getEnergy() - energyCost));
        pet.setHunger(Math.max(0, pet.getHunger() - hungerCost));
        pet.setStatus(mapBusyStatus(type));
        petMapperUpdate(pet);
        return toVo(activity, pet.getName());
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

    private boolean isEligible(Pet pet, Integer requiredLevel, Integer energyCost, Integer hungerCost) {
        return pet.getLevel() >= (requiredLevel != null ? requiredLevel : 1)
                && pet.getEnergy() >= (energyCost != null ? energyCost : 0)
                && pet.getHunger() >= (hungerCost != null ? hungerCost : 0);
    }

    /** 加载可领取活动（兼容入口）：稳定序 finished_at desc, id desc 取一条 */
    private PetActivity requireClaimableActivity(Long userId, PetActivityType type) {
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, type.name())
                .in(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getFinishedAt)
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有可领取的任务");
        }
        lazyCompleteIfFinished(activity);
        return activity;
    }

    /** 惰性流转 IN_PROGRESS → COMPLETED（定时扫描的兜底） */
    private void lazyCompleteIfFinished(PetActivity activity) {
        if (!PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            return;
        }
        if (activity.getFinishedAt().isAfter(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FINISHED, "任务还没完成，再等等吧");
        }
        activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                .eq(PetActivity::getId, activity.getId())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        activity.setStatus(PetActivityStatus.COMPLETED.name());
    }

    /** 领取 CAS：IN_PROGRESS/COMPLETED → CLAIMED（返回影响行数，0=已领取） */
    private int claimActivityCas(PetActivity activity) {
        return activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                .set(PetActivity::getClaimedAt, petClock.nowUtc())
                .eq(PetActivity::getId, activity.getId())
                .in(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name()));
    }

    /** 奖励归属宠物（B03）：按 activity.petId 加载，不取当前主宠 */
    private Pet requireActivityPet(PetActivity activity) {
        Pet pet = petMapper.selectById(activity.getPetId());
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "执行任务的宠物不存在");
        }
        return pet;
    }

    /** 智力收益加成百分比：min(25, 智力×0.5)（原文档 §12 智力影响工作收益/学习速度） */
    static int intelligenceBonusPercent(int intelligence) {
        return Math.min(25, intelligence / 2);
    }

    /** 惰性过期：COMPLETED 超时未领取置 EXPIRED（供扫描器与查询入口共用） */
    @Override
    public int expireStaleClaims() {
        return activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.EXPIRED.name())
                .eq(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                .le(PetActivity::getFinishedAt, petClock.nowUtc().minusHours(CLAIM_EXPIRE_HOURS)));
    }

    private void petMapperUpdate(Pet pet) {
        // B02：乐观锁更新必须检查影响行数，版本冲突显式失败（可重试），禁止静默丢更新
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "宠物状态被并发修改，请稍后重试");
        }
    }

    private String mapBusyStatus(PetActivityType type) {
        return switch (type) {
            case WORK, CAREER_WORK -> PetStatus.WORKING.name();
            case STUDY -> PetStatus.STUDYING.name();
            case BOTTLE_FISHING -> PetStatus.FISHING.name();
            case REST, FEED, PLAY, CLEAN, VISIT, EVOLVE -> PetStatus.IDLE.name();
        };
    }

    private void notifyLevelUp(Pet pet, int levelups) {
        if (levelups <= 0) {
            return;
        }
        achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        String eventId = "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel();
        outboxService.record(eventId, RocketMQConfig.PET_TAG_LEVEL_UP, pet.getUserId(), pet.getId(),
                new PetEventProducer.PetEventMessage(
                        eventId, String.valueOf(pet.getUserId()), "PET_LEVEL_UP",
                        "宠物升级啦！",
                        pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                        String.valueOf(pet.getId()), "PET_LEVEL_UP"));
    }

    private PetActivityVO toVo(PetActivity activity, String petName) {
        LocalDateTime now = petClock.nowUtc();
        String status = activity.getStatus();
        boolean inProgress = PetActivityStatus.IN_PROGRESS.name().equals(status);
        long remaining = inProgress
                ? Math.max(0, DurationSupport.secondsBetween(now, activity.getFinishedAt()))
                : 0;
        // 前端据此切换"进行中/去领取"按钮：COMPLETED，或 IN_PROGRESS 但已过完成时间
        boolean canClaim = PetActivityStatus.COMPLETED.name().equals(status)
                || (inProgress && !activity.getFinishedAt().isAfter(now));
        return new PetActivityVO(activity.getId(), activity.getPetId(), petName,
                activity.getActivityType(), activity.getConfigId(),
                resolveConfigName(activity), status, activity.getStartedAt(), activity.getFinishedAt(),
                remaining, canClaim, activity.getFinishedAt().plusHours(CLAIM_EXPIRE_HOURS),
                activity.getClaimedAt(), activity.getResult());
    }

    /** 活动关联的岗位/课程名（快照优先，B09 运营改名不改已开始任务展示） */
    private String resolveConfigName(PetActivity activity) {
        Map<String, Object> snapshot = PetJsonUtils.parse(activity.getSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        if (snapshot != null && snapshot.get("name") != null) {
            return String.valueOf(snapshot.get("name"));
        }
        if (activity.getConfigId() == null) {
            return null;
        }
        return switch (activity.getActivityType()) {
            case "WORK" -> {
                PetJobConfig job = jobConfigMapper.selectById(activity.getConfigId());
                yield job == null ? null : job.getName();
            }
            case "STUDY" -> {
                PetStudyConfig study = studyConfigMapper.selectById(activity.getConfigId());
                yield study == null ? null : study.getName();
            }
            default -> null;
        };
    }

    private int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** 静态工具：秒差计算 */
    static final class DurationSupport {
        private DurationSupport() {
        }

        static long secondsBetween(LocalDateTime from, LocalDateTime to) {
            return java.time.Duration.between(from, to).getSeconds();
        }
    }
}
