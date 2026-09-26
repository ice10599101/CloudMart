package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
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
import java.util.HashMap;
import java.util.Map;

/**
 * 宠物捞漂流瓶实现（B11）。
 *
 * <p>冷却独立于领取状态：nextFishingAt = 最近一次任务完成时间 + cooldownSeconds，
 * CLAIMED 不再绕过冷却。开始时冻结成功率与随机种子到活动快照，结算结果确定——
 * 查询、失败重试、服务重启都不重新抽取。结算与 FAILED 重试统一走
 * {@link PetBottleSettlementService}（独立事务 Bean，避免自调用事务失效）。</p>
 */
@Service
@Slf4j
public class PetBottleFishingServiceImpl implements PetBottleFishingService {

    private static final int START_ENERGY_COST = 10;
    /** 捞瓶活动在统一 PetActivityVO 中的展示名（无 pet_*_config 关联，兜底文案） */
    private static final String BOTTLE_ACTIVITY_NAME = "捞漂流瓶";
    /** 领取有效期与统一活动一致（小时） */
    private static final long CLAIM_EXPIRE_HOURS = 72;

    private final PetService petService;
    private final PetActivityMapper activityMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetMapper petMapper;
    private final PetBottleSettlementService settlementService;
    private final PetProperties properties;
    private final PetClock petClock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PetBottleFishingServiceImpl(PetService petService,
                                       PetActivityMapper activityMapper,
                                       PetBottleRecordMapper bottleRecordMapper,
                                       PetMapper petMapper,
                                       PetBottleSettlementService settlementService,
                                       PetProperties properties,
                                       PetClock petClock) {
        this.petService = petService;
        this.activityMapper = activityMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.petMapper = petMapper;
        this.settlementService = settlementService;
        this.properties = properties;
        this.petClock = petClock;
    }

    @Override
    public PetBottleStatusVO status(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetActivity activity = latestActivity(userId);

        boolean fishing = false;
        boolean canClaim = false;
        long remaining = 0;
        String lastOutcome = null;
        Long lastBottleId = null;
        Long activityId = null;
        LocalDateTime startedAt = null;
        LocalDateTime finishedAt = null;
        LocalDateTime now = petClock.nowUtc();
        LocalDateTime nextFishingAt = nextFishingAt(userId);

        if (activity != null) {
            if (PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
                activityId = activity.getId();
                startedAt = activity.getStartedAt();
                finishedAt = activity.getFinishedAt();
                if (finishedAt.isAfter(now)) {
                    fishing = true;
                    remaining = Math.max(0, Duration.between(now, finishedAt).getSeconds());
                } else {
                    // 惰性结算（经独立事务 Bean 代理调用，保证事务生效；CAS 幂等）
                    settlementService.settleActivity(pet, activity);
                    activity = activityMapper.selectById(activity.getId());
                    PetBottleRecord record = findRecord(activity.getId());
                    if (record != null) {
                        lastOutcome = record.getOutcome();
                        lastBottleId = record.getBottleId();
                    }
                    canClaim = PetActivityStatus.COMPLETED.name().equals(activity.getStatus());
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
        long cooldownRemaining = nextFishingAt == null ? 0
                : Math.max(0, Duration.between(now, nextFishingAt).getSeconds());
        return new PetBottleStatusVO(fishing, activityId, startedAt, finishedAt, remaining, canClaim,
                cooldownRemaining, nextFishingAt, now, properties.getBottle().getDurationSeconds(),
                lastOutcome, lastBottleId,
                settlementService.estimateSuccessRate(pet), settlementService.unlockedArea(pet.getLevel()));
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
        // B11：冷却独立于领取状态（CLAIMED 也占用冷却），原子校验由唯一活动约束 + 时间窗判断
        LocalDateTime nextFishingAt = nextFishingAt(userId);
        if (nextFishingAt != null && nextFishingAt.isAfter(petClock.nowUtc())) {
            throw new BusinessException(PetErrorCodes.PET_BOTTLE_COOLDOWN, "宠物刚回来还在休息，过一会再去捞吧");
        }

        LocalDateTime now = petClock.nowUtc();
        // 冻结成功率与随机种子（B11：配置变更/重启不改变已开始游戏的结果）
        double rate = settlementService.estimateSuccessRate(pet);
        long seed = secureRandom.nextLong();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("rate", rate);
        snapshot.put("seed", seed);
        snapshot.put("durationSeconds", properties.getBottle().getDurationSeconds());

        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(userId);
        activity.setActivityType(PetActivityType.BOTTLE_FISHING.name());
        activity.setStatus(PetActivityStatus.IN_PROGRESS.name());
        activity.setStartedAt(now);
        activity.setFinishedAt(now.plusSeconds(properties.getBottle().getDurationSeconds()));
        activity.setSnapshot(PetJsonUtils.toJson(snapshot));
        try {
            activityMapper.insert(activity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在捞瓶子啦");
        }
        pet.setEnergy(pet.getEnergy() - START_ENERGY_COST);
        pet.setStatus(PetStatus.FISHING.name());
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "宠物状态被并发修改，请稍后重试");
        }
        return toActivityVo(activity);
    }

    /** 兼容入口：领取最近一次任务 */
    @Override
    @Transactional
    public PetActivityVO claim(Long userId) {
        PetActivity activity = latestActivity(userId);
        if (activity == null) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有可领取的捞瓶任务");
        }
        return claimByActivity(userId, activity);
    }

    /** 按 activityId 领取（B03/B11）：旧结果始终可按 ID 找到；结算→重试→CAS 领取 */
    @Override
    @Transactional
    public PetActivityVO claimByActivity(Long userId, PetActivity activity) {
        if (!activity.getUserId().equals(userId)
                || !PetActivityType.BOTTLE_FISHING.name().equals(activity.getActivityType())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有这个捞瓶任务");
        }
        Pet pet = requireActivityPet(activity);
        if (PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            if (activity.getFinishedAt().isAfter(petClock.nowUtc())) {
                throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FINISHED, "任务还没完成，再等等吧");
            }
            settlementService.settleActivity(pet, activity);
            activity = activityMapper.selectById(activity.getId());
        }
        PetBottleRecord record = findRecord(activity.getId());
        if (record != null && PetBottleOutcome.FAILED.name().equals(record.getOutcome())) {
            // 心愿服务曾失败：按原种子语义重试远程打捞
            settlementService.retryFailedRecord(pet, activity, record);
            record = findRecord(activity.getId());
            activity = activityMapper.selectById(activity.getId());
        }
        if (!PetActivityStatus.COMPLETED.name().equals(activity.getStatus())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FINISHED, "任务还没完成，再等等吧");
        }
        int claimed = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                .set(PetActivity::getClaimedAt, petClock.nowUtc())
                .eq(PetActivity::getId, activity.getId())
                .eq(PetActivity::getStatus, PetActivityStatus.COMPLETED.name()));
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "这次捞瓶结果已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        return toActivityVo(activity);
    }

    /** 定时扫描器入口（按活动结算；经代理调用，事务生效） */
    @Override
    @Transactional
    public PetActivityVO settle(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetActivity activity = latestActivity(userId);
        if (activity == null || !PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            return activity != null ? toActivityVo(activity) : null;
        }
        if (activity.getFinishedAt().isAfter(petClock.nowUtc())) {
            return toActivityVo(activity);
        }
        return settlementService.settleActivity(pet, activity);
    }

    // ---------------- 内部 ----------------

    /**
     * 下次可开始时间（B11）：最近一次已完成/已领取任务的完成时间 + 冷却。
     * 独立于领取状态——CLAIMED 不再绕过冷却；IN_PROGRESS 由互斥拦截。
     */
    private LocalDateTime nextFishingAt(Long userId) {
        PetActivity lastFinished = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.BOTTLE_FISHING.name())
                .in(PetActivity::getStatus, PetActivityStatus.COMPLETED.name(), PetActivityStatus.CLAIMED.name())
                .orderByDesc(PetActivity::getFinishedAt)
                .last("LIMIT 1"));
        if (lastFinished == null || lastFinished.getFinishedAt() == null) {
            return null;
        }
        return lastFinished.getFinishedAt().plusSeconds(properties.getBottle().getCooldownSeconds());
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

    private Pet requireActivityPet(PetActivity activity) {
        Pet pet = petMapper.selectById(activity.getPetId());
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "执行任务的宠物不存在");
        }
        return pet;
    }

    private PetActivityVO toActivityVo(PetActivity activity) {
        LocalDateTime now = petClock.nowUtc();
        boolean inProgress = PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus());
        long remaining = inProgress ? Math.max(0, Duration.between(now, activity.getFinishedAt()).getSeconds()) : 0;
        boolean canClaim = PetActivityStatus.COMPLETED.name().equals(activity.getStatus());
        return new PetActivityVO(activity.getId(), activity.getPetId(), null,
                activity.getActivityType(), activity.getConfigId(),
                BOTTLE_ACTIVITY_NAME, activity.getStatus(), activity.getStartedAt(), activity.getFinishedAt(),
                remaining, canClaim, activity.getFinishedAt().plusHours(CLAIM_EXPIRE_HOURS),
                activity.getClaimedAt(), activity.getResult());
    }
}
