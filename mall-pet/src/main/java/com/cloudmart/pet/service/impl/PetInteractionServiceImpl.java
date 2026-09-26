package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetHomeService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetInteractionService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetActionVO;
import com.cloudmart.pet.vo.PetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础互动实现（B06 收益额度、休息状态机与防刷；数值全部来自 PetProperties，服务端权威）。
 *
 * <p>额度：喂食（5 次/日，按用户共享，失败/已饱不消耗）与玩耍收益（10 次/日）、休息亲密度
 * （3 次/日）全部数据库权威（Redis 故障期间奖励不无限发放）；额度超限的玩耍转无收益动画互动，
 * 不推进奖励型任务/成就/亲密度。</p>
 *
 * <p>休息：10 分钟定时长期活动（复用 pet_activity 状态机与用户级互斥），到期自动应用恢复，
 * 本轮无领取入口、不可取消（不靠取消获益）；全满状态下开始休息直接 409 且不消耗次数。</p>
 */
@Service
@Slf4j
public class PetInteractionServiceImpl implements PetInteractionService {

    private final PetService petService;
    private final PetStateService stateService;
    private final PetActivityMapper activityMapper;
    private final PetMapper petMapper;
    private final PetAchievementService achievementService;
    private final PetDailyQuestService dailyQuestService;
    private final PetIntimacyService intimacyService;
    private final PetHomeService homeService;
    private final PetProperties properties;
    private final PetQuotaService quotaService;
    private final com.cloudmart.pet.service.impl.PetCompanionFeatureService companionFeatureService;
    private final com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService;
    private final PetOutboxService outboxService;
    private final PetClock petClock;

    public PetInteractionServiceImpl(PetService petService,
                                     PetStateService stateService,
                                     PetActivityMapper activityMapper,
                                     PetMapper petMapper,
                                     PetAchievementService achievementService,
                                     PetDailyQuestService dailyQuestService,
                                     PetIntimacyService intimacyService,
                                     PetHomeService homeService,
                                     PetProperties properties,
                                     PetQuotaService quotaService,
                                     PetOutboxService outboxService,
                                     PetClock petClock,
                                     com.cloudmart.pet.service.impl.PetCompanionFeatureService companionFeatureService,
                                     com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService) {
        this.petService = petService;
        this.stateService = stateService;
        this.activityMapper = activityMapper;
        this.petMapper = petMapper;
        this.achievementService = achievementService;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.homeService = homeService;
        this.properties = properties;
        this.quotaService = quotaService;
        this.outboxService = outboxService;
        this.petClock = petClock;
        this.companionFeatureService = companionFeatureService;
        this.playFeatureService = playFeatureService;
    }

    @Override
    @Transactional
    public PetVO feed(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();

        // 先验状态后扣额度：失败/已饱请求不消耗次数（§2.4）
        if (pet.getHunger() >= 100) {
            throw new BusinessException(PetErrorCodes.PET_STATE_FULL, "宠物已经吃饱啦，先陪它玩一会吧");
        }
        if (!quotaService.tryConsume(userId, PetQuotaService.QuotaType.FEED, 0, cfg.getFeedDailyLimit())) {
            throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED,
                    "今天已经喂了 " + cfg.getFeedDailyLimit() + " 次啦，明天再来吧");
        }
        // B02：加法属性原子增量（并发喂食不丢属性），状态列单写
        petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .setSql("hunger = LEAST(hunger + " + cfg.getFeedHunger() + ", 100)")
                .setSql("happiness = LEAST(happiness + " + cfg.getFeedHappiness() + ", 100)")
                .setSql("hp = LEAST(hp + " + cfg.getFeedHp() + ", max_hp)")
                .set(Pet::getStatus, PetStatus.IDLE.name())
                .set(Pet::getHungerFrac, 0.0)
                .eq(Pet::getId, pet.getId()));
        pet.setHunger(Math.min(100, pet.getHunger() + cfg.getFeedHunger()));
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getFeedHappiness()));
        pet.setHp(Math.min(pet.getMaxHp(), pet.getHp() + cfg.getFeedHp()));
        pet.setStatus(PetStatus.IDLE.name());
        pet.setHungerFrac(0.0);
        recordInstantActivity(pet, PetActivityType.FEED, cfg.getFeedExp());
        companionFeatureService.recordStep(userId, "FEED");
        playFeatureService.recordContribution(userId, "FEED:" + pet.getId() + ":" + petClock.nowUtc().toLocalDate());
        intimacyService.gain(pet, PetIntimacySource.FEED);
        int levelups = stateService.grantExp(pet, cfg.getFeedExp());
        dailyQuestService.record(pet, PetQuestType.FEED, 1);
        achievementService.evaluate(pet, PetAchievementService.Event.FEED);
        notifyLevelUpIfAny(pet, levelups);
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO play(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();

        // 长期活动进行中（工作/读书/捞瓶/休息）：只允许无收益动画互动，不改状态不推进任何进度
        if (hasBusyActivity(userId)) {
            return petService.getMyPet(userId);
        }
        if (pet.getEnergy() < cfg.getPlayEnergy()) {
            throw new BusinessException(PetErrorCodes.PET_ENERGY_INSUFFICIENT,
                    "宠物没有力气玩了，让它休息一下吧");
        }
        // 收益额度（数据库权威）：超限转无收益动画互动——不推进经验/亲密度/任务/成就
        boolean rewardable = quotaService.tryConsume(userId, PetQuotaService.QuotaType.PLAY_REWARD, 0,
                cfg.getPlayRewardDailyLimit());
        // B02：原子增量（消耗与心情回填并发安全）
        petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .setSql("energy = GREATEST(energy - " + cfg.getPlayEnergy() + ", 0)")
                .setSql("happiness = LEAST(happiness + " + cfg.getPlayHappiness() + ", 100)")
                .set(Pet::getStatus, PetStatus.IDLE.name())
                .eq(Pet::getId, pet.getId()));
        pet.setEnergy(Math.max(0, pet.getEnergy() - cfg.getPlayEnergy()));
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getPlayHappiness()));
        pet.setStatus(PetStatus.IDLE.name());
        recordInstantActivity(pet, PetActivityType.PLAY, rewardable ? cfg.getPlayExp() : 0);
        companionFeatureService.recordStep(userId, "PLAY");
        if (rewardable) {
            playFeatureService.recordContribution(userId, "PLAY:" + pet.getId() + ":" + petClock.nowUtc().toLocalDate());
            intimacyService.gain(pet, PetIntimacySource.PLAY);
            int levelups = stateService.grantExp(pet, cfg.getPlayExp());
            dailyQuestService.record(pet, PetQuestType.PLAY, 1);
            achievementService.evaluate(pet, PetAchievementService.Event.PLAY);
            notifyLevelUpIfAny(pet, levelups);
        } else {
            log.debug("玩耍今日收益额度已耗尽，转为无收益动画互动: userId={}", userId);
        }
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO clean(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();

        if (pet.getCleanliness() > 90) {
            throw new BusinessException(PetErrorCodes.PET_STATE_FULL, "宠物现在很干净，不需要洗澡哦");
        }
        // B02：原子增量
        petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .setSql("cleanliness = LEAST(cleanliness + " + cfg.getCleanCleanliness() + ", 100)")
                .setSql("happiness = LEAST(happiness + " + cfg.getCleanHappiness() + ", 100)")
                .set(Pet::getStatus, PetStatus.IDLE.name())
                .set(Pet::getCleanlinessFrac, 0.0)
                .eq(Pet::getId, pet.getId()));
        pet.setCleanliness(Math.min(100, pet.getCleanliness() + cfg.getCleanCleanliness()));
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getCleanHappiness()));
        pet.setStatus(PetStatus.IDLE.name());
        pet.setCleanlinessFrac(0.0);
        recordInstantActivity(pet, PetActivityType.CLEAN, cfg.getCleanExp());
        intimacyService.gain(pet, PetIntimacySource.CLEAN);
        int levelups = stateService.grantExp(pet, cfg.getCleanExp());
        dailyQuestService.record(pet, PetQuestType.CLEAN, 1);
        achievementService.evaluate(pet, PetAchievementService.Event.CLEAN);
        notifyLevelUpIfAny(pet, levelups);
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO rest(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();

        // 长期活动互斥（每用户一条进行中）
        if (hasBusyActivity(userId)) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物正在忙，忙完再休息吧");
        }
        // 全满状态拒绝无效休息：不消耗次数、不加亲密度（B06）
        if (pet.getEnergy() >= 100 && pet.getHp() >= pet.getMaxHp()) {
            throw new BusinessException(PetErrorCodes.PET_STATE_FULL, "宠物精力充沛，不需要休息哦");
        }
        LocalDateTime now = petClock.nowUtc();
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(userId);
        activity.setActivityType(PetActivityType.REST.name());
        activity.setStatus(PetActivityStatus.IN_PROGRESS.name());
        activity.setStartedAt(now);
        activity.setFinishedAt(now.plusSeconds(cfg.getRestDurationSeconds()));
        activity.setSnapshot(com.cloudmart.pet.util.PetJsonUtils.toJson(java.util.Map.of("restHunger", cfg.getRestHunger())));
        try {
            activityMapper.insert(activity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在忙另一件事啦");
        }
        // 开始时保留配置中的饱食代价（§2.4 休息边界）；状态切换为 RESTING
        pet.setHunger(Math.max(0, pet.getHunger() - cfg.getRestHunger()));
        pet.setHungerFrac(0.0);
        pet.setStatus(PetStatus.RESTING.name());
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "宠物状态被并发修改，请稍后重试");
        }
        return petService.getMyPet(userId);
    }

    /** 定时休息到期结算（幂等 CAS）：恢复精力/生命 + 舒适度心情加成 + 额度内亲密度 */
    @Override
    @Transactional
    public PetVO settleRest(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.REST.name())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity != null && !activity.getFinishedAt().isAfter(petClock.nowUtc())) {
            int applied = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                    .set(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                    .set(PetActivity::getClaimedAt, petClock.nowUtc())
                    .set(PetActivity::getResult, "{\"rest\":\"APPLIED\"}")
                    .eq(PetActivity::getId, activity.getId())
                    .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
            if (applied > 0) {
                applyRestEffects(pet);
            }
        }
        return petService.getMyPet(userId);
    }

    /** 休息恢复效果（到期自动应用）：精力/生命回满 + 家园舒适度心情加成 + 额度内亲密度 */
    private void applyRestEffects(Pet pet) {
        PetProperties.Interaction cfg = properties.getInteraction();
        pet.setEnergy(100);
        pet.setHp(pet.getMaxHp());
        pet.setEnergyFrac(0.0);
        int comfortBonus = homeService.comfortRestHappinessBonus(pet.getId());
        if (comfortBonus > 0) {
            pet.setHappiness(Math.min(100, pet.getHappiness() + comfortBonus));
        }
        pet.setStatus(PetStatus.IDLE.name());
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "宠物状态被并发修改，请稍后重试");
        }
        if (quotaService.tryConsume(pet.getUserId(), PetQuotaService.QuotaType.REST_INTIMACY, 0,
                cfg.getRestIntimacyDailyLimit())) {
            intimacyService.gain(pet, PetIntimacySource.REST);
            dailyQuestService.record(pet, PetQuestType.REST, 1);
            achievementService.evaluate(pet, PetAchievementService.Event.REST);
        } else {
            log.debug("休息亲密度今日额度已耗尽: userId={}", pet.getUserId());
        }
    }

    private boolean hasBusyActivity(Long userId) {
        return activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())) > 0;
    }

    /** 即时行为留痕：直接 CLAIMED（成就 ACTIVITY_COUNT 依此计数） */
    private void recordInstantActivity(Pet pet, PetActivityType type, int expGain) {
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(pet.getUserId());
        activity.setActivityType(type.name());
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        LocalDateTime now = petClock.nowUtc();
        activity.setStartedAt(now);
        activity.setFinishedAt(now);
        activity.setClaimedAt(now);
        activity.setResult("{\"exp\":" + expGain + "}");
        activityMapper.insert(activity);
    }

    /** 升级：评估成就 + outbox 可靠事件（与其他玩法同一口径） */
    private void notifyLevelUpIfAny(Pet pet, int levelups) {
        if (levelups <= 0) {
            return;
        }
        achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        String eventId = "LEVEL_UP:" + pet.getId() + ":" + pet.getLevel();
        outboxService.record(eventId, com.cloudmart.pet.config.RocketMQConfig.PET_TAG_LEVEL_UP, pet.getUserId(), pet.getId(),
                new com.cloudmart.pet.mq.PetEventProducer.PetEventMessage(
                        eventId, String.valueOf(pet.getUserId()), "PET_LEVEL_UP",
                        "宠物升级啦！",
                        pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                        String.valueOf(pet.getId()), "PET_LEVEL_UP"));
    }

    @Override
    public List<PetActionVO> actions(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();
        boolean busy = hasBusyActivity(userId);
        List<PetActionVO> result = new ArrayList<>();

        int feedRemaining = quotaService.remaining(userId, PetQuotaService.QuotaType.FEED, 0, cfg.getFeedDailyLimit());
        result.add(new PetActionVO("FEED", pet.getHunger() < 100 && feedRemaining > 0,
                pet.getHunger() >= 100 ? "PET_STATE_FULL" : (feedRemaining <= 0 ? "PET_QUOTA_EXHAUSTED" : null),
                pet.getHunger() >= 100 ? "宠物已经吃饱啦" : (feedRemaining <= 0 ? "今日喂食次数已用完" : null),
                null, feedRemaining));

        int playRemaining = quotaService.remaining(userId, PetQuotaService.QuotaType.PLAY_REWARD, 0, cfg.getPlayRewardDailyLimit());
        boolean playAllowed = pet.getEnergy() >= cfg.getPlayEnergy() && !busy;
        result.add(new PetActionVO("PLAY", playAllowed,
                pet.getEnergy() < cfg.getPlayEnergy() ? "PET_ENERGY_INSUFFICIENT" : (busy ? "PET_USER_BUSY" : null),
                pet.getEnergy() < cfg.getPlayEnergy() ? "宠物没有力气玩了" : (busy ? "宠物正在忙" : null),
                null, playRemaining));

        result.add(new PetActionVO("CLEAN", pet.getCleanliness() <= 90,
                pet.getCleanliness() > 90 ? "PET_STATE_FULL" : null,
                pet.getCleanliness() > 90 ? "宠物现在很干净" : null,
                null, null));

        PetActivity restActivity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.REST.name())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        boolean resting = restActivity != null;
        boolean restFull = pet.getEnergy() >= 100 && pet.getHp() >= pet.getMaxHp();
        result.add(new PetActionVO("REST", !restFull && !busy && !resting,
                restFull ? "PET_STATE_FULL" : (busy || resting ? "PET_USER_BUSY" : null),
                restFull ? "宠物精力充沛，不需要休息" : (resting ? "休息进行中" : (busy ? "宠物正在忙" : null)),
                resting ? restActivity.getFinishedAt() : null,
                quotaService.remaining(userId, PetQuotaService.QuotaType.REST_INTIMACY, 0, cfg.getRestIntimacyDailyLimit())));
        return result;
    }
}
