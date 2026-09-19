package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetInteractionService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 基础互动实现（数值全部来自 PetProperties，服务端权威）。
 *
 * <p>并发语义：喂食/玩耍/清洁走"读-改-乐观锁写"（@Version，写失败抛冲突重试）；
 * 喂食日限频 Redis INCR（Fail-Open：Redis 故障放行并告警，饱食上限封死收益）。
 * 每次互动落 pet_activity 即时留痕（原文档 §10 记录宠物行为），
 * 成就 ACTIVITY_COUNT 依此计数。</p>
 */
@Service
@Slf4j
public class PetInteractionServiceImpl implements PetInteractionService {

    private final PetService petService;
    private final PetStateService stateService;
    private final PetActivityMapper activityMapper;
    private final PetMapper petMapper;
    private final PetAchievementService achievementService;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final PetEventProducer eventProducer;

    public PetInteractionServiceImpl(PetService petService,
                                     PetStateService stateService,
                                     PetActivityMapper activityMapper,
                                     PetMapper petMapper,
                                     PetAchievementService achievementService,
                                     PetProperties properties,
                                     StringRedisTemplate redisTemplate,
                                     PetEventProducer eventProducer) {
        this.petService = petService;
        this.stateService = stateService;
        this.activityMapper = activityMapper;
        this.petMapper = petMapper;
        this.achievementService = achievementService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.eventProducer = eventProducer;
    }

    @Override
    @Transactional
    public PetVO feed(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();

        if (pet.getHunger() >= 100) {
            throw new BusinessException(PetErrorCodes.PET_STATE_FULL, "宠物已经吃饱啦，先陪它玩一会吧");
        }
        // 先验状态后扣限频，避免 409 时误耗每日次数
        consumeFeedQuota(userId);
        pet.setHunger(Math.min(100, pet.getHunger() + cfg.getFeedHunger()));
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getFeedHappiness()));
        pet.setHp(Math.min(pet.getMaxHp(), pet.getHp() + cfg.getFeedHp()));
        pet.setStatus(PetStatus.IDLE.name());
        recordInstantActivity(pet, PetActivityType.FEED, cfg.getFeedExp());
        int levelups = stateService.grantExp(pet, cfg.getFeedExp());
        achievementService.evaluate(pet, PetAchievementService.Event.FEED);
        notifyLevelUpIfAny(userId, pet, levelups);
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO play(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();

        if (pet.getEnergy() < cfg.getPlayEnergy()) {
            throw new BusinessException(PetErrorCodes.PET_ENERGY_INSUFFICIENT,
                    "宠物没有力气玩了，让它休息一下吧");
        }
        pet.setEnergy(Math.max(0, pet.getEnergy() - cfg.getPlayEnergy()));
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getPlayHappiness()));
        pet.setStatus(PetStatus.IDLE.name());
        recordInstantActivity(pet, PetActivityType.PLAY, cfg.getPlayExp());
        int levelups = stateService.grantExp(pet, cfg.getPlayExp());
        achievementService.evaluate(pet, PetAchievementService.Event.PLAY);
        notifyLevelUpIfAny(userId, pet, levelups);
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
        pet.setCleanliness(Math.min(100, pet.getCleanliness() + cfg.getCleanCleanliness()));
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getCleanHappiness()));
        pet.setStatus(PetStatus.IDLE.name());
        recordInstantActivity(pet, PetActivityType.CLEAN, cfg.getCleanExp());
        int levelups = stateService.grantExp(pet, cfg.getCleanExp());
        achievementService.evaluate(pet, PetAchievementService.Event.CLEAN);
        notifyLevelUpIfAny(userId, pet, levelups);
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO rest(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetProperties.Interaction cfg = properties.getInteraction();

        Long busy = activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
        if (busy > 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物正在忙，忙完再休息吧");
        }
        pet.setEnergy(100);
        pet.setHp(pet.getMaxHp());
        pet.setHunger(Math.max(0, pet.getHunger() - cfg.getRestHunger()));
        pet.setStatus(PetStatus.IDLE.name());
        recordInstantActivity(pet, PetActivityType.REST, 0);
        petMapper.updateById(pet);
        achievementService.evaluate(pet, PetAchievementService.Event.REST);
        return petService.getMyPet(userId);
    }

    /** 喂食日限频：INCR + TTL 至当日 UTC 23:59；Redis 故障 Fail-Open 放行（仅告警） */
    private void consumeFeedQuota(Long userId) {
        try {
            String key = String.format(PetServiceImpl.KEY_FEED_COUNTER, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            int dailyLimit = properties.getInteraction().getFeedDailyLimit();
            if (used != null && used > dailyLimit) {
                throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED,
                        "今天已经喂了 " + dailyLimit + " 次啦，明天再来吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("喂食限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
        }
    }

    /** 即时行为留痕：直接 CLAIMED（成就 ACTIVITY_COUNT 依此计数）；状态变更随后随 grantExp 一并落库 */
    private void recordInstantActivity(Pet pet, PetActivityType type, int expGain) {
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(pet.getUserId());
        activity.setActivityType(type.name());
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        activity.setStartedAt(now);
        activity.setFinishedAt(now);
        activity.setClaimedAt(now);
        activity.setResult("{\"exp\":" + expGain + "}");
        activityMapper.insert(activity);
    }

    /** 升级：评估成就 + 发送 MQ 事件（与打工/读书/对战同一口径，供通知侧生成宠物提醒） */
    private void notifyLevelUpIfAny(Long userId, Pet pet, int levelups) {
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
}
