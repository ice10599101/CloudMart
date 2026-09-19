package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
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
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetJobConfigMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetStudyConfigMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetActivityService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 统一活动服务实现（打工/读书）。
 *
 * <p>幂等领取（原文档 §87）：{@code UPDATE ... SET status='CLAIMED'
 * WHERE id=? AND status IN ('IN_PROGRESS','COMPLETED')} 条件更新，
 * 影响行数 0 即重复领取（409）；疯狂点击/双端并发只成功一次。</p>
 *
 * <p>奖励一致性：领取 CAS 成功后同一事务内先发经验（本地库），再经 Feign 发星光；
 * 星光发放失败抛 503 → 整个事务回滚（领取也回滚），用户稍后重试即可，
 * 不出现"已领取但没发钱"的假成功（AGENTS §17 明确失败 > 假装成功）。</p>
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
    private final WishFeignClient wishFeignClient;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetStatsService statsService;

    public PetActivityServiceImpl(PetService petService,
                                  PetStateService stateService,
                                  PetActivityMapper activityMapper,
                                  PetJobConfigMapper jobConfigMapper,
                                  PetStudyConfigMapper studyConfigMapper,
                                  PetMapper petMapper,
                                  WishFeignClient wishFeignClient,
                                  PetAchievementService achievementService,
                                  PetEventProducer eventProducer,
                                  PetStatsService statsService) {
        this.petService = petService;
        this.stateService = stateService;
        this.activityMapper = activityMapper;
        this.jobConfigMapper = jobConfigMapper;
        this.studyConfigMapper = studyConfigMapper;
        this.petMapper = petMapper;
        this.wishFeignClient = wishFeignClient;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.statsService = statsService;
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
                job.getDurationSeconds(), job.getEnergyCost(), job.getHungerCost(), job.getRequiredLevel());
    }

    @Override
    @Transactional
    public PetActivityVO startStudy(Long userId, StartStudyRequest request) {
        PetStudyConfig study = studyConfigMapper.selectById(request.configId());
        if (study == null || !Boolean.TRUE.equals(study.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_STUDY_NOT_FOUND, "这门课程不存在或已下架");
        }
        return startTimedActivity(userId, PetActivityType.STUDY, study.getId(), study.getName(),
                study.getDurationSeconds(), study.getEnergyCost(), 0, study.getRequiredLevel());
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

    @Override
    @Transactional
    public PetActivityVO claimWork(Long userId) {
        PetActivity activity = requireClaimableActivity(userId, PetActivityType.WORK);
        Pet pet = petService.requireOwnedPet(userId);
        PetJobConfig job = jobConfigMapper.selectById(activity.getConfigId());

        int claimed = claimActivity(activity);
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        // 奖励：经验（本地）+ 星光（Feign，失败抛 503 → 事务整体回滚，可安全重试）
        // 智力影响工作收益（原文档 §12）：加成 = min(25%, 智力×0.5%)
        int expReward = job != null ? job.getExpReward() : 0;
        int currencyReward = job != null ? job.getCurrencyReward() : 0;
        int intelligenceBonus = intelligenceBonusPercent(pet.getIntelligence());
        expReward = expReward + Math.round(expReward * intelligenceBonus / 100f);
        currencyReward = currencyReward + Math.round(currencyReward * intelligenceBonus / 100f);
        int levelups = stateService.grantExp(pet, expReward);
        if (currencyReward > 0) {
            wishFeignClient.earnStarlight(userId, currencyReward, activity.getId());
        }
        activity.setResult(PetJsonUtils.toJson(Map.of(
                "exp", expReward, "currency", currencyReward, "intelligenceBonus", intelligenceBonus,
                "configId", activity.getConfigId() != null ? activity.getConfigId() : 0)));
        activityMapper.updateById(activity);

        achievementService.evaluate(pet, PetAchievementService.Event.WORK_CLAIMED);
        notifyLevelUp(userId, pet, levelups);
        return toVo(activity);
    }

    @Override
    @Transactional
    public PetActivityVO claimStudy(Long userId) {
        PetActivity activity = requireClaimableActivity(userId, PetActivityType.STUDY);
        Pet pet = petService.requireOwnedPet(userId);
        PetStudyConfig study = studyConfigMapper.selectById(activity.getConfigId());

        int claimed = claimActivity(activity);
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        int expReward = study != null ? study.getExpReward() : 0;
        int intelligenceReward = study != null ? study.getIntelligenceReward() : 0;
        // 智力影响学习速度（原文档 §12）：读书经验加成 = min(25%, 智力×0.5%)
        int intelligenceBonus = intelligenceBonusPercent(pet.getIntelligence());
        expReward = expReward + Math.round(expReward * intelligenceBonus / 100f);
        // 技能被动"博览群书"（原文档 §89）：读书经验额外加成
        int skillBonusPercent = (int) Math.round(statsService.studyExpBonus(pet) * 100);
        expReward = expReward + Math.round(expReward * skillBonusPercent / 100f);
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

        achievementService.evaluate(pet, PetAchievementService.Event.STUDY_CLAIMED);
        notifyLevelUp(userId, pet, levelups);
        return toVo(activity);
    }

    // ---------------- 内部共用 ----------------

    private PetActivityVO startTimedActivity(Long userId, PetActivityType type, Long configId, String configName,
                                             Integer durationSeconds, Integer energyCost, Integer hungerCost,
                                             Integer requiredLevel) {
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

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(userId);
        activity.setActivityType(type.name());
        activity.setConfigId(configId);
        activity.setStatus(PetActivityStatus.IN_PROGRESS.name());
        activity.setStartedAt(now);
        activity.setFinishedAt(now.plusSeconds(durationSeconds != null ? durationSeconds : 0));
        try {
            activityMapper.insert(activity);
        } catch (DuplicateKeyException e) {
            // 并发开工：uk_activity_user_active 函数唯一索引兜底
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在忙另一件事啦");
        }

        // 立即扣消耗（不欠账）：精力/饥饿同一次乐观锁写入
        pet.setEnergy(Math.max(0, pet.getEnergy() - energyCost));
        pet.setHunger(Math.max(0, pet.getHunger() - hungerCost));
        pet.setStatus(mapBusyStatus(type));
        petMapperUpdate(pet);
        return toVo(activity);
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

    /** 加载可领取活动：IN_PROGRESS（需已到完成时间，顺带惰性流转）或 COMPLETED */
    private PetActivity requireClaimableActivity(Long userId, PetActivityType type) {
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, type.name())
                .in(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有可领取的任务");
        }
        if (PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            if (activity.getFinishedAt().isAfter(LocalDateTime.now(ZoneId.of("UTC")))) {
                throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FINISHED, "任务还没完成，再等等吧");
            }
            // 惰性流转 IN_PROGRESS → COMPLETED（定时扫描的兜底），随后按 COMPLETED 领取
            activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                    .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                    .eq(PetActivity::getId, activity.getId())
                    .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
            activity.setStatus(PetActivityStatus.COMPLETED.name());
        }
        return activity;
    }

    /** 领取 CAS：IN_PROGRESS/COMPLETED → CLAIMED（返回影响行数，0=已领取） */
    private int claimActivity(PetActivity activity) {
        return activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                .set(PetActivity::getClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetActivity::getId, activity.getId())
                .in(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name()));
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
                .le(PetActivity::getFinishedAt,
                        LocalDateTime.now(ZoneId.of("UTC")).minusHours(CLAIM_EXPIRE_HOURS)));
    }

    private void petMapperUpdate(Pet pet) {
        // 乐观锁更新（@Version 自动附加版本条件），并发写失败抛冲突由上层统一处理
        petMapper.updateById(pet);
    }

    private String mapBusyStatus(PetActivityType type) {
        return switch (type) {
            case WORK -> PetStatus.WORKING.name();
            case STUDY -> PetStatus.STUDYING.name();
            case BOTTLE_FISHING -> PetStatus.FISHING.name();
            case REST, FEED, PLAY, CLEAN, VISIT, EVOLVE -> PetStatus.IDLE.name();
        };
    }

    private void notifyLevelUp(Long userId, Pet pet, int levelups) {
        if (levelups <= 0) {
            return;
        }
        achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        eventProducer.publish(RocketMQConfig.PET_TAG_LEVEL_UP, new PetEventProducer.PetEventMessage(
                userId, "PET_LEVEL_UP",
                "宠物升级啦！",
                pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                pet.getId(), "PET_LEVEL_UP"));
    }

    private PetActivityVO toVo(PetActivity activity) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String status = activity.getStatus();
        boolean inProgress = PetActivityStatus.IN_PROGRESS.name().equals(status);
        long remaining = inProgress
                ? Math.max(0, DurationSupport.secondsBetween(now, activity.getFinishedAt()))
                : 0;
        // 前端据此切换"进行中/去领取"按钮：COMPLETED，或 IN_PROGRESS 但已过完成时间
        boolean canClaim = PetActivityStatus.COMPLETED.name().equals(status)
                || (inProgress && !activity.getFinishedAt().isAfter(now));
        return new PetActivityVO(activity.getId(), activity.getActivityType(), activity.getConfigId(),
                resolveConfigName(activity), status, activity.getStartedAt(), activity.getFinishedAt(),
                remaining, canClaim, activity.getClaimedAt(), activity.getResult());
    }

    /** 活动关联的岗位/课程名（捞瓶/即时行为无 configId，返回 null） */
    private String resolveConfigName(PetActivity activity) {
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

    /** 静态工具：秒差计算 */
    static final class DurationSupport {
        private DurationSupport() {
        }

        static long secondsBetween(LocalDateTime from, LocalDateTime to) {
            return java.time.Duration.between(from, to).getSeconds();
        }
    }
}
