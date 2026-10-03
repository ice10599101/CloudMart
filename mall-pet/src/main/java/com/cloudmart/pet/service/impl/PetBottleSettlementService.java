package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.enums.PetBottleRarity;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetActivityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

/**
 * 捞瓶结算处理器（B11）：真正生效的独立事务 Bean——读接口/定时任务经代理调用，
 * 不依赖本类自调用上的事务注解（原实现 status 内直接 this.settle 自调用，事务不生效）。
 *
 * <p>结果确定性（B11）：开始时冻结成功率与随机种子到活动快照，结算按种子重放，
 * 查询/重试/服务重启不重新抽取更好结果；FAILED 重试只重试远程打捞（稳定 requestId），
 * 不重抽稀有度。</p>
 */
@Service
@Slf4j
public class PetBottleSettlementService {

    /** 结算经验：捞到 +15 / 空手 +5；稀有 +30 / 宠物瓶与彩蛋 +20 */
    private static final int EXP_CAUGHT = 15;
    private static final int EXP_EMPTY = 5;
    private static final int EXP_RARE = 30;
    private static final int EXP_SPECIAL = 20;
    /** 稀有瓶星光加成 */
    static final int RARE_STARLIGHT = 50;

    /** 捞瓶区域解锁等级（原文档 §20） */
    private static final String[] AREA_NAMES = {"社区池塘", "城市河流", "神秘海域", "深海区域"};
    private static final int[] AREA_LEVELS = {1, 5, 10, 20};

    private final PetActivityMapper activityMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetMapper petMapper;
    private final WishFeignClient wishFeignClient;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetBottleContentProvider contentProvider;
    private final PetStatsService statsService;
    private final PetStateService stateService;
    private final PetDailyQuestService dailyQuestService;
    private final PetIntimacyService intimacyService;
    private final PetEconomyService economyService;
    private final com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService;
    private final PetOutboxService outboxService;
    private final PetProperties properties;
    private final PetClock petClock;

    public PetBottleSettlementService(PetActivityMapper activityMapper,
                                      PetBottleRecordMapper bottleRecordMapper,
                                      PetMapper petMapper,
                                      WishFeignClient wishFeignClient,
                                      PetAchievementService achievementService,
                                      PetEventProducer eventProducer,
                                      PetBottleContentProvider contentProvider,
                                      PetStatsService statsService,
                                      PetStateService stateService,
                                      PetDailyQuestService dailyQuestService,
                                      PetIntimacyService intimacyService,
                                      PetEconomyService economyService,
                                      com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService,
                                      PetOutboxService outboxService,
                                      PetProperties properties,
                                      PetClock petClock) {
        this.activityMapper = activityMapper;
        this.bottleRecordMapper = bottleRecordMapper;
        this.petMapper = petMapper;
        this.wishFeignClient = wishFeignClient;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.contentProvider = contentProvider;
        this.statsService = statsService;
        this.stateService = stateService;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.economyService = economyService;
        this.playFeatureService = playFeatureService;
        this.outboxService = outboxService;
        this.properties = properties;
        this.petClock = petClock;
    }

    /**
     * 结算一个捞瓶活动（CAS IN_PROGRESS→COMPLETED 保证只执行一次）。
     * 远程打捞带稳定请求标识（BOTTLE_FISH:{activityId}）与显式主人身份。
     *
     * <p>R30：结算对象固定为 activity.petId 归属宠物（内部加载并校验归属），
     * 不接受调用方传入的任意 Pet 实体——A 开始捞瓶后切到 B，到期结果与经验归 A。</p>
     */
    @Transactional
    public PetActivityVO settleActivity(PetActivity activity) {
        Pet pet = requireActivityPet(activity);
        int updated = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                .eq(PetActivity::getId, activity.getId())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        if (updated == 0) {
            // 并发扫描器/另一请求已结算
            return toActivityVo(activityMapper.selectById(activity.getId()));
        }
        activity.setStatus(PetActivityStatus.COMPLETED.name());

        Map<String, Object> snapshot = PetJsonUtils.parse(activity.getSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        // 快照冻结的成功率与种子（存量无快照活动回退当前估算 + 服务端时间种子）
        double rate = snapshot != null && snapshot.get("rate") != null
                ? ((Number) snapshot.get("rate")).doubleValue() : estimateSuccessRate(pet);
        long seed = snapshot != null && snapshot.get("seed") != null
                ? ((Number) snapshot.get("seed")).longValue() : petClock.nowInstant().toEpochMilli();
        Random random = new Random(seed);

        PetBottleOutcome outcome;
        PetBottleRarity rarity = PetBottleRarity.NORMAL;
        String specialContent = null;
        Long bottleId = null;
        int expGain;
        if (random.nextDouble() >= rate) {
            outcome = PetBottleOutcome.EMPTY;
            expGain = EXP_EMPTY;
        } else {
            rarity = rollRarity(random);
            if (rarity == PetBottleRarity.NORMAL) {
                // 普通瓶走 mall-wish 真实瓶子池；稳定请求标识保证超时重试不二次抢瓶
                String requestId = "BOTTLE_FISH:" + activity.getId();
                try {
                    WishFeignClient.WishBottleVO bottle = wishFeignClient
                            .fishForPet(activity.getUserId(), requestId).data();
                    if (bottle == null) {
                        outcome = PetBottleOutcome.EMPTY;
                        expGain = EXP_EMPTY;
                    } else {
                        outcome = PetBottleOutcome.CAUGHT;
                        bottleId = bottle.bottleId();
                        expGain = EXP_CAUGHT;
                    }
                } catch (Exception e) {
                    // 心愿服务降级：FAILED 可按原种子重试领取，不吞奖励、不重抽结果
                    log.warn("宠物捞瓶远程打捞失败，标记 FAILED 待重试: activityId={}", activity.getId(), e);
                    outcome = PetBottleOutcome.FAILED;
                    expGain = 0;
                }
            } else {
                outcome = PetBottleOutcome.CAUGHT;
                specialContent = contentProvider.pick(rarity);
                expGain = switch (rarity) {
                    case RARE -> EXP_RARE;
                    case PET, EASTER_EGG -> EXP_SPECIAL;
                    default -> EXP_CAUGHT;
                };
            }
        }

        try {
            PetBottleRecord record = new PetBottleRecord();
            record.setPetId(pet.getId());
            record.setUserId(activity.getUserId());
            record.setActivityId(activity.getId());
            record.setBottleId(bottleId);
            record.setOutcome(outcome.name());
            record.setRarity(rarity.name());
            record.setSpecialContent(specialContent);
            record.setSuccessRate(rate);
            record.setStartedAt(activity.getStartedAt());
            record.setFinishedAt(activity.getFinishedAt());
            bottleRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 扫描器已结算（uk_pet_bottle_record_activity）：返回既有结果
            return toActivityVo(activityMapper.selectById(activity.getId()));
        }

        activity.setResult(resultJson(outcome, bottleId, expGain, rate, rarity, specialContent));
        activityMapper.updateById(activity);
        applySettlementRewards(pet, activity, outcome, rarity, bottleId, expGain);
        return toActivityVo(activity);
    }

    /** FAILED 重试（同种子语义）：只重试远程打捞，稀有度/结果不变；仍失败保持 FAILED 可重试 */
    @Transactional
    public PetActivityVO retryFailedRecord(PetActivity activity, PetBottleRecord record) {
        Pet pet = requireActivityPet(activity);
        String requestId = "BOTTLE_FISH:" + activity.getId();
        WishFeignClient.WishBottleVO bottle;
        try {
            bottle = wishFeignClient.fishForPet(activity.getUserId(), requestId).data();
        } catch (Exception e) {
            log.warn("FAILED 重试仍失败, activityId={}", activity.getId(), e);
            return toActivityVo(activity);
        }
        PetBottleOutcome outcome = bottle == null ? PetBottleOutcome.EMPTY : PetBottleOutcome.CAUGHT;
        int expGain = outcome == PetBottleOutcome.CAUGHT ? EXP_CAUGHT : EXP_EMPTY;
        record.setOutcome(outcome.name());
        record.setBottleId(bottle != null ? bottle.bottleId() : null);
        bottleRecordMapper.updateById(record);
        activity.setResult(resultJson(outcome, record.getBottleId(), expGain,
                record.getSuccessRate() != null ? record.getSuccessRate() : 0,
                PetBottleRarity.NORMAL, record.getSpecialContent()));
        activityMapper.updateById(activity);
        applySettlementRewards(pet, activity, outcome, PetBottleRarity.NORMAL, record.getBottleId(), expGain);
        return toActivityVo(activity);
    }

    /**
     * 结算奖励（经验/亲密度/任务/成就/星光/通知）。
     * R31：FAILED（远程打捞不可用）不是"已完成参与"——不推进亲密度/任务/成就/经验，
     * 只把宠物从 FISHING 释放为 IDLE；奖励事实仅对 CAUGHT/EMPTY 终态一次性发放
     * （retry 成功时从 FAILED 首次进入终态，天然只发一次，重复重试零副作用）。
     */
    private void applySettlementRewards(Pet pet, PetActivity activity, PetBottleOutcome outcome,
                                        PetBottleRarity rarity, Long bottleId, int expGain) {
        pet.setStatus(PetStatus.IDLE.name());
        if (outcome == PetBottleOutcome.FAILED) {
            // 远程不可用：无奖励事实，仅释放忙碌状态（重试成功后经本方法首次入账）
            int updated = petMapper.updateById(pet);
            if (updated == 0) {
                throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                        "宠物状态被并发修改，请稍后重试");
            }
            return;
        }
        intimacyService.gain(pet, PetIntimacySource.BOTTLE);
        if (expGain > 0) {
            int levelups = stateService.grantExp(pet, expGain);
            if (levelups > 0) {
                achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
                String eventId = "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel();
                outboxService.record(eventId, RocketMQConfig.PET_TAG_LEVEL_UP, activity.getUserId(),
                        activity.getPetId(),
                        new PetEventProducer.PetEventMessage(
                                eventId, String.valueOf(activity.getUserId()), "PET_LEVEL_UP",
                                "宠物升级啦！",
                                pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                                String.valueOf(pet.getId()), "PET_LEVEL_UP"));
            }
        } else {
            int updated = petMapper.updateById(pet);
            if (updated == 0) {
                throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                        "宠物状态被并发修改，请稍后重试");
            }
        }
        dailyQuestService.record(pet, PetQuestType.BOTTLE, 1);
        achievementService.evaluate(pet, PetAchievementService.Event.BOTTLE_SETTLED);
        if (outcome == PetBottleOutcome.CAUGHT) {
            // B02/BE-12：捞瓶结果事实接入图鉴投影
            playFeatureService.unlockCollection(activity.getUserId(), activity.getPetId(),
                    "BOTTLE", rarity.name(), "BOTTLE_CAUGHT:" + activity.getId());
        }
        if (outcome == PetBottleOutcome.CAUGHT) {
            if (rarity == PetBottleRarity.RARE) {
                PetEconomyService.WalletSettlement settlement = economyService.earn(
                        activity.getUserId(), activity.getPetId(),
                        "BOTTLE_REWARD", activity.getId(), RARE_STARLIGHT, null, activity.getId());
                if (!settlement.isCompleted()) {
                    log.info("稀有瓶星光结算中, activityId={}, status={}", activity.getId(), settlement.status());
                }
            }
            String eventId = "BOTTLE_CAUGHT:" + activity.getId();
            outboxService.record(eventId, RocketMQConfig.PET_TAG_BOTTLE_CAUGHT, activity.getUserId(),
                    activity.getPetId(),
                    new PetEventProducer.PetEventMessage(
                            eventId, String.valueOf(activity.getUserId()), "PET_BOTTLE_CAUGHT",
                            "宠物捞到漂流瓶啦！",
                            rarity == PetBottleRarity.NORMAL
                                    ? pet.getName() + " 帮你捞到了一只漂流瓶，快去打开看看吧！"
                                    : pet.getName() + " 捞到了一只" + rarityLabel(rarity) + "！快去看看吧！",
                            bottleId != null ? String.valueOf(bottleId) : "0",
                            "PET_BOTTLE_CAUGHT"));
        }
    }

    /** R30：奖励归属宠物——按 activity.petId 加载并校验与活动归属用户一致，不取当前主宠 */
    private Pet requireActivityPet(PetActivity activity) {
        Pet pet = petMapper.selectById(activity.getPetId());
        if (pet == null || !activity.getUserId().equals(pet.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "执行任务的宠物不存在");
        }
        return pet;
    }

    PetBottleRarity rollRarity(Random random) {        double roll = random.nextDouble();
        if (roll < 0.08) {
            return PetBottleRarity.RARE;
        }
        if (roll < 0.16) {
            return PetBottleRarity.PET;
        }
        if (roll < 0.20) {
            return PetBottleRarity.EASTER_EGG;
        }
        return PetBottleRarity.NORMAL;
    }

    /** 成功率 = base + 敏捷×bonus + 等级×bonus + 区域加成 + 技能被动，封顶 max（开始时快照冻结） */
    double estimateSuccessRate(Pet pet) {
        PetProperties.Bottle cfg = properties.getBottle();
        int agility = statsService.combatStats(pet).agility();
        double rate = cfg.getBaseSuccessRate()
                + agility * cfg.getAgilityBonusRate()
                + pet.getLevel() * cfg.getLevelBonusRate()
                + unlockedAreaIndex(pet.getLevel()) * 0.01
                + statsService.bottleSuccessBonus(pet);
        return Math.min(cfg.getMaxSuccessRate(), rate);
    }

    int unlockedAreaIndex(int level) {
        int index = 0;
        for (int i = 0; i < AREA_LEVELS.length; i++) {
            if (level >= AREA_LEVELS[i]) {
                index = i;
            }
        }
        return index;
    }

    String unlockedArea(int level) {
        String area = AREA_NAMES[0];
        for (int i = 0; i < AREA_LEVELS.length; i++) {
            if (level >= AREA_LEVELS[i]) {
                area = AREA_NAMES[i];
            }
        }
        return area;
    }

    private String rarityLabel(PetBottleRarity rarity) {
        return switch (rarity) {
            case RARE -> "稀有瓶";
            case PET -> "宠物瓶";
            case EASTER_EGG -> "彩蛋瓶";
            default -> "漂流瓶";
        };
    }

    private String resultJson(PetBottleOutcome outcome, Long bottleId, int expGain, double rate,
                              PetBottleRarity rarity, String specialContent) {
        return PetJsonUtils.toJson(Map.of(
                "outcome", outcome.name(),
                "rarity", rarity.name(),
                "specialContent", specialContent != null ? specialContent : "",
                "bottleId", bottleId != null ? bottleId : 0,
                "exp", expGain,
                "successRate", rate));
    }

    private PetActivityVO toActivityVo(PetActivity activity) {
        if (activity == null) {
            return null;
        }
        LocalDateTime now = petClock.nowUtc();
        boolean inProgress = PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus());
        long remaining = inProgress
                ? Math.max(0, java.time.Duration.between(now, activity.getFinishedAt()).getSeconds()) : 0;
        boolean canClaim = PetActivityStatus.COMPLETED.name().equals(activity.getStatus());
        return new PetActivityVO(activity.getId(), activity.getPetId(), null,
                activity.getActivityType(), activity.getConfigId(),
                "捞漂流瓶", activity.getStatus(), activity.getStartedAt(), activity.getFinishedAt(),
                remaining, canClaim, activity.getFinishedAt().plusHours(72),
                activity.getClaimedAt(), activity.getResult());
    }
}
