package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetBottleFishingService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetBottleStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 宠物捞漂流瓶实现。
 *
 * <p>成功率（原文档 §18，服务端 roll）：70% + 敏捷×0.5% + 等级×1%，封顶 95%；
 * roll 成功后调 mall-wish {@code /internal/pet-support/drift-bottles/fish}
 * （计入用户每日打捞配额，天然防刷）；海里无瓶视为空手而归。
 * Feign 降级 → outcome=FAILED，任务保持可领取，用户重试即可，不吞奖励。</p>
 *
 * <p>结算时机三重保障（互为幂等，CAS 抢占）：
 * 定时扫描器（分钟级）/ 用户查状态 / 用户点领取。</p>
 */
@Service
@Slf4j
public class PetBottleFishingServiceImpl implements PetBottleFishingService {

    /** 结算经验：捞到 +15 / 空手 +5（参与即有成长，原文档 §20 捞瓶与成长关联） */
    private static final int EXP_CAUGHT = 15;
    private static final int EXP_EMPTY = 5;
    private static final int START_ENERGY_COST = 10;

    /** 捞瓶区域解锁等级（原文档 §20，一期仅文案展示） */
    private static final String[] AREA_NAMES = {"社区池塘", "城市河流", "神秘海域", "深海区域"};
    private static final int[] AREA_LEVELS = {1, 5, 10, 20};

    private final PetService petService;
    private final PetStateService stateService;
    private final PetActivityMapper activityMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetMapper petMapper;
    private final WishFeignClient wishFeignClient;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PetBottleFishingServiceImpl(PetService petService,
                                       PetStateService stateService,
                                       PetActivityMapper activityMapper,
                                       PetBottleRecordMapper bottleRecordMapper,
                                       PetMapper petMapper,
                                       WishFeignClient wishFeignClient,
                                       PetAchievementService achievementService,
                                       PetEventProducer eventProducer,
                                       PetProperties properties) {
        this.petService = petService;
        this.stateService = stateService;
        this.activityMapper = activityMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.petMapper = petMapper;
        this.wishFeignClient = wishFeignClient;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.properties = properties;
    }

    @Override
    public PetBottleStatusVO status(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetActivity activity = latestActivity(userId);

        boolean fishing = false;
        boolean canClaim = false;
        long remaining = 0;
        long cooldownRemaining = 0;
        String lastOutcome = null;
        Long lastBottleId = null;
        Long activityId = null;
        LocalDateTime startedAt = null;
        LocalDateTime finishedAt = null;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

        if (activity != null) {
            if (PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
                activityId = activity.getId();
                startedAt = activity.getStartedAt();
                finishedAt = activity.getFinishedAt();
                if (finishedAt.isAfter(now)) {
                    fishing = true;
                    remaining = Math.max(0, Duration.between(now, finishedAt).getSeconds());
                } else {
                    // 惰性结算（读路径触发，保证"回来就能看到结果"）
                    settle(userId);
                    activity = activityMapper.selectById(activity.getId());
                    PetBottleRecord record = findRecord(activity.getId());
                    if (record != null) {
                        lastOutcome = record.getOutcome();
                        lastBottleId = record.getBottleId();
                    }
                    canClaim = PetActivityStatus.COMPLETED.name().equals(activity.getStatus());
                    cooldownRemaining = cooldownRemaining(activity.getFinishedAt(), now);
                }
            } else if (PetActivityStatus.COMPLETED.name().equals(activity.getStatus())) {
                canClaim = true;
                PetBottleRecord record = findRecord(activity.getId());
                if (record != null) {
                    lastOutcome = record.getOutcome();
                    lastBottleId = record.getBottleId();
                }
            }
        }
        return new PetBottleStatusVO(fishing, activityId, startedAt, finishedAt, remaining, canClaim,
                cooldownRemaining, lastOutcome, lastBottleId,
                estimateSuccessRate(pet), unlockedArea(pet.getLevel()));
    }

    @Override
    @Transactional
    public PetActivityVO start(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);

        Long busy = activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        if (busy > 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在忙另一件事啦");
        }
        if (pet.getEnergy() < START_ENERGY_COST) {
            throw new BusinessException(PetErrorCodes.PET_ENERGY_INSUFFICIENT, "宠物没有力气去海边啦，先休息一下吧");
        }
        PetActivity latest = latestActivity(userId);
        if (latest != null && PetActivityStatus.COMPLETED.name().equals(latest.getStatus())
                && latest.getFinishedAt().isAfter(LocalDateTime.now(ZoneId.of("UTC"))
                .minusSeconds(properties.getBottle().getCooldownSeconds()))) {
            throw new BusinessException(PetErrorCodes.PET_BOTTLE_COOLDOWN, "宠物刚回来还在休息，过一会再去捞吧");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(userId);
        activity.setActivityType(PetActivityType.BOTTLE_FISHING.name());
        activity.setStatus(PetActivityStatus.IN_PROGRESS.name());
        activity.setStartedAt(now);
        activity.setFinishedAt(now.plusSeconds(properties.getBottle().getDurationSeconds()));
        try {
            activityMapper.insert(activity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在捞瓶子啦");
        }
        pet.setEnergy(pet.getEnergy() - START_ENERGY_COST);
        pet.setStatus(PetStatus.FISHING.name());
        petMapper.updateById(pet);
        return toActivityVo(activity);
    }

    @Override
    @Transactional
    public PetActivityVO claim(Long userId) {
        settle(userId);
        PetActivity activity = latestActivity(userId);
        if (activity == null) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有可领取的捞瓶任务");
        }
        PetBottleRecord record = findRecord(activity.getId());
        if (record != null && PetBottleOutcome.FAILED.name().equals(record.getOutcome())) {
            // 心愿服务曾降级：重试一次捞瓶
            retryFailedRecord(userId, activity, record);
            record = findRecord(activity.getId());
            activity = activityMapper.selectById(activity.getId());
        }
        if (!PetActivityStatus.COMPLETED.name().equals(activity.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FINISHED, "任务还没完成，再等等吧");
        }
        int claimed = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                .set(PetActivity::getClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetActivity::getId, activity.getId())
                .eq(PetActivity::getStatus, PetActivityStatus.COMPLETED.name()));
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "这次捞瓶结果已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        return toActivityVo(activity);
    }

    /**
     * 任务结算（CAS IN_PROGRESS→COMPLETED 保证只执行一次）：
     * roll 成功率 → Feign 捞瓶 → 落流水 → 经验 → 通知。
     * Feign 失败仅标记 FAILED（不抛出），任务保持可重试领取。
     */
    @Override
    @Transactional
    public PetActivityVO settle(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetActivity activity = latestActivity(userId);
        if (activity == null || !PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            return activity != null ? toActivityVo(activity) : null;
        }
        if (activity.getFinishedAt().isAfter(LocalDateTime.now(ZoneId.of("UTC")))) {
            return toActivityVo(activity);
        }

        int updated = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                .eq(PetActivity::getId, activity.getId())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        if (updated == 0) {
            // 并发扫描器/另一请求已结算：重读（防御性 null 兜底走本地对象）
            PetActivity latest = activityMapper.selectById(activity.getId());
            return toActivityVo(latest != null ? latest : activity);
        }
        // 本地对象同步 CAS 结果，后续 VO 构建不再依赖二次查询
        activity.setStatus(PetActivityStatus.COMPLETED.name());

        double rate = estimateSuccessRate(pet);
        boolean rollSuccess = ThreadLocalRandom.current().nextDouble() < rate;

        PetBottleOutcome outcome;
        Long bottleId = null;
        int expGain;
        if (!rollSuccess) {
            outcome = PetBottleOutcome.EMPTY;
            expGain = EXP_EMPTY;
        } else {
            try {
                WishFeignClient.WishBottleVO bottle = wishFeignClient.fishForPet().data();
                if (bottle == null) {
                    // 海里暂时没有瓶子：空手而归（原文档 §16）
                    outcome = PetBottleOutcome.EMPTY;
                    expGain = EXP_EMPTY;
                } else {
                    outcome = PetBottleOutcome.CAUGHT;
                    bottleId = bottle.bottleId();
                    expGain = EXP_CAUGHT;
                }
            } catch (BusinessException e) {
                // 心愿服务降级：FAILED 可重试领取，不吞奖励
                log.warn("宠物捞瓶 Feign 降级，标记 FAILED 待重试: userId={}", userId, e);
                outcome = PetBottleOutcome.FAILED;
                expGain = 0;
            }
        }

        try {
            PetBottleRecord record = new PetBottleRecord();
            record.setPetId(pet.getId());
            record.setUserId(userId);
            record.setActivityId(activity.getId());
            record.setBottleId(bottleId);
            record.setOutcome(outcome.name());
            record.setSuccessRate(rate);
            record.setStartedAt(activity.getStartedAt());
            record.setFinishedAt(activity.getFinishedAt());
            bottleRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 扫描器已结算：重读既有结果（防御性 null 兜底走本地对象）
            PetActivity latest = activityMapper.selectById(activity.getId());
            return toActivityVo(latest != null ? latest : activity);
        }

        activity.setResult(resultJson(outcome, bottleId, expGain, rate));
        activityMapper.updateById(activity);
        if (expGain > 0) {
            pet.setStatus(PetStatus.IDLE.name());
            int levelups = stateService.grantExp(pet, expGain);
            if (levelups > 0) {
                achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
            }
        }
        achievementService.evaluate(pet, PetAchievementService.Event.BOTTLE_SETTLED);
        if (outcome == PetBottleOutcome.CAUGHT) {
            eventProducer.publish(RocketMQConfig.PET_TAG_BOTTLE_CAUGHT, new PetEventProducer.PetEventMessage(
                    userId, "PET_BOTTLE_CAUGHT",
                    "宠物捞到漂流瓶啦！",
                    pet.getName() + " 帮你捞到了一只漂流瓶，快去打开看看吧！",
                    bottleId, "PET_BOTTLE_CAUGHT"));
        }
        return toActivityVo(activity);
    }

    /** FAILED 重试：重新尝试捞瓶并更新流水（活动保持 COMPLETED，可反复重试） */
    private void retryFailedRecord(Long userId, PetActivity activity, PetBottleRecord record) {
        Pet pet = petService.requireOwnedPet(userId);
        WishFeignClient.WishBottleVO bottle = wishFeignClient.fishForPet().data();
        PetBottleOutcome outcome = bottle == null ? PetBottleOutcome.EMPTY : PetBottleOutcome.CAUGHT;
        record.setOutcome(outcome.name());
        record.setBottleId(bottle != null ? bottle.bottleId() : null);
        bottleRecordMapper.updateById(record);
        activity.setResult(resultJson(record));
        activityMapper.updateById(activity);
        stateService.grantExp(pet, outcome == PetBottleOutcome.CAUGHT ? EXP_CAUGHT : EXP_EMPTY);
        if (outcome == PetBottleOutcome.CAUGHT) {
            eventProducer.publish(RocketMQConfig.PET_TAG_BOTTLE_CAUGHT, new PetEventProducer.PetEventMessage(
                    userId, "PET_BOTTLE_CAUGHT",
                    "宠物捞到漂流瓶啦！",
                    pet.getName() + " 帮你捞到了一只漂流瓶，快去打开看看吧！",
                    record.getBottleId(), "PET_BOTTLE_CAUGHT"));
        }
    }

    /** 成功率 = base + 敏捷×bonus + 等级×bonus，封顶 max（原文档 §18） */
    double estimateSuccessRate(Pet pet) {
        PetProperties.Bottle cfg = properties.getBottle();
        double rate = cfg.getBaseSuccessRate()
                + pet.getAgility() * cfg.getAgilityBonusRate()
                + pet.getLevel() * cfg.getLevelBonusRate();
        return Math.min(cfg.getMaxSuccessRate(), rate);
    }

    private String unlockedArea(int level) {
        String area = AREA_NAMES[0];
        for (int i = 0; i < AREA_LEVELS.length; i++) {
            if (level >= AREA_LEVELS[i]) {
                area = AREA_NAMES[i];
            }
        }
        return area;
    }

    private long cooldownRemaining(LocalDateTime finishedAt, LocalDateTime now) {
        long elapsed = Duration.between(finishedAt, now).getSeconds();
        return Math.max(0, properties.getBottle().getCooldownSeconds() - elapsed);
    }

    private PetBottleRecord findRecord(Long activityId) {
        return bottleRecordMapper.selectOne(new LambdaQueryWrapper<PetBottleRecord>()
                .eq(PetBottleRecord::getActivityId, activityId));
    }

    private PetActivity latestActivity(Long userId) {
        return activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.BOTTLE_FISHING.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
    }

    private String resultJson(PetBottleOutcome outcome, Long bottleId, int expGain, double rate) {
        return PetJsonUtils.toJson(Map.of(
                "outcome", outcome.name(),
                "bottleId", bottleId != null ? bottleId : 0,
                "exp", expGain,
                "successRate", rate));
    }

    private String resultJson(PetBottleRecord record) {
        return resultJson(PetBottleOutcome.valueOf(record.getOutcome()),
                record.getBottleId(), PetBottleOutcome.CAUGHT.name().equals(record.getOutcome()) ? EXP_CAUGHT : EXP_EMPTY,
                record.getSuccessRate() != null ? record.getSuccessRate() : 0);
    }

    private PetActivityVO toActivityVo(PetActivity activity) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        boolean inProgress = PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus());
        long remaining = inProgress ? Math.max(0, Duration.between(now, activity.getFinishedAt()).getSeconds()) : 0;
        boolean canClaim = PetActivityStatus.COMPLETED.name().equals(activity.getStatus());
        return new PetActivityVO(activity.getId(), activity.getActivityType(), activity.getConfigId(),
                null, activity.getStatus(), activity.getStartedAt(), activity.getFinishedAt(),
                remaining, canClaim, activity.getClaimedAt(), activity.getResult());
    }
}
