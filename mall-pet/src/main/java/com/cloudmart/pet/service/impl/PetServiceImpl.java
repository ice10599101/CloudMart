package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.CreatePetRequest;
import com.cloudmart.pet.dto.RenamePetRequest;
import com.cloudmart.pet.dto.UpdateAppearanceRequest;
import com.cloudmart.pet.dto.UpdatePrivacyRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetAchievementRecord;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.repository.PetAchievementRecordMapper;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetReminderService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetPublicVO;
import com.cloudmart.pet.vo.PetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 宠物基础服务实现：领养/查询/改名/外观/隐私/公开资料。
 *
 * <p>喂食日计数走 Redis（TTL 至当日 UTC 23:59），Fail-Open：Redis 异常时
 * 返回 null（前端隐藏余量、不限次）——饱食度上限 100 本身封死重复喂食收益，
 * 限频服务故障不阻断主流程（实施文档 §1.5）。</p>
 */
@Service
@Slf4j
public class PetServiceImpl implements PetService {

    /** Redis Key：宠物模块限频计数，规范 {service}:{module}:{type}:{id}:{date} */
    static final String KEY_FEED_COUNTER = "pet:ratelimit:feed:%d:%s";

    static final int FEED_DAILY_LIMIT = 5;
    static final long RENAME_COOLDOWN_DAYS = 30;

    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetAchievementRecordMapper achievementRecordMapper;
    private final PetStateService stateService;
    private final PetReminderService reminderService;
    private final StringRedisTemplate redisTemplate;

    public PetServiceImpl(PetMapper petMapper,
                          PetActivityMapper activityMapper,
                          PetAchievementRecordMapper achievementRecordMapper,
                          PetStateService stateService,
                          PetReminderService reminderService,
                          StringRedisTemplate redisTemplate) {
        this.petMapper = petMapper;
        this.activityMapper = activityMapper;
        this.achievementRecordMapper = achievementRecordMapper;
        this.stateService = stateService;
        this.reminderService = reminderService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PetVO getMyPet(Long userId) {
        Pet pet = requireOwnedPet(userId);
        PetVO vo = toVo(pet, feedRemainingToday(userId));

        // 主动消息触发器惰性评估（每日问候/长期未陪伴/饿了/社区播报/捞瓶完成），
        // 失败不阻断宠物页主流程（Fail-Open，实施文档 §1.10）
        try {
            reminderService.evaluateOnVisit(userId, pet);
        } catch (Exception e) {
            log.warn("宠物主动消息评估失败（Fail-Open）: userId={}", userId, e);
        }
        return vo;
    }

    @Override
    @Transactional
    public PetVO createPet(Long userId, CreatePetRequest request) {
        if (stateService.findByUserId(userId) != null) {
            throw new BusinessException(PetErrorCodes.PET_ALREADY_EXISTS, "你已经有一只宠物啦，要好好照顾它哦");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        Pet pet = new Pet();
        pet.setUserId(userId);
        pet.setName(request.name().trim());
        pet.setSpecies(request.species());
        pet.setAppearance(PetJsonUtils.toJson(Map.of(
                "color", request.color() != null ? request.color() : "orange",
                "accessory", request.accessory() != null ? request.accessory() : "none")));
        pet.setPersonality(request.personality());
        pet.setLevel(1);
        pet.setExp(0);
        pet.setGrowthStage(stateService.growthStageFor(1));
        pet.setHp(100);
        pet.setMaxHp(100);
        pet.setHunger(80);
        pet.setHappiness(80);
        pet.setEnergy(100);
        pet.setCleanliness(90);
        pet.setStrength(5);
        pet.setIntelligence(5);
        pet.setAgility(5);
        pet.setCharm(5);
        pet.setStatus(PetStatus.IDLE.name());
        pet.setIsPublic(true);
        pet.setLastStateUpdateAt(now);
        try {
            petMapper.insert(pet);
        } catch (DuplicateKeyException e) {
            // 并发领养：uk_pet_user 数据库层兜底
            throw new BusinessException(PetErrorCodes.PET_ALREADY_EXISTS, "你已经有一只宠物啦，要好好照顾它哦");
        }
        return toVo(pet, null);
    }

    @Override
    @Transactional
    public PetVO renamePet(Long userId, RenamePetRequest request) {
        Pet pet = requireOwnedPet(userId);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        if (pet.getLastRenamedAt() != null
                && Duration.between(pet.getLastRenamedAt(), now).toDays() < RENAME_COOLDOWN_DAYS) {
            throw new BusinessException(PetErrorCodes.PET_RENAME_COOLDOWN,
                    "改名太频繁啦，" + RENAME_COOLDOWN_DAYS + " 天内只能改一次名字");
        }
        pet.setName(request.name().trim());
        pet.setLastRenamedAt(now);
        petMapper.updateById(pet);
        return toVo(pet, feedRemainingToday(userId));
    }

    @Override
    @Transactional
    public PetVO updateAppearance(Long userId, UpdateAppearanceRequest request) {
        Pet pet = requireOwnedPet(userId);
        pet.setAppearance(PetJsonUtils.toJson(Map.of("color", request.color(), "accessory", request.accessory())));
        petMapper.updateById(pet);
        return toVo(pet, feedRemainingToday(userId));
    }

    @Override
    @Transactional
    public void updatePrivacy(Long userId, UpdatePrivacyRequest request) {
        Pet pet = requireOwnedPet(userId);
        int updated = petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .set(Pet::getIsPublic, request.isPublic())
                .eq(Pet::getId, pet.getId())
                .eq(Pet::getUserId, userId));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能修改自己的宠物");
        }
    }

    @Override
    public PetPublicVO getPublicPet(Long ownerId) {
        Pet pet = stateService.findByUserId(ownerId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "这位用户还没有宠物");
        }
        if (!Boolean.TRUE.equals(pet.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_PUBLIC, "主人把宠物藏起来了");
        }
        Long achievementCount = achievementRecordMapper.selectCount(new LambdaQueryWrapper<PetAchievementRecord>()
                .eq(PetAchievementRecord::getPetId, pet.getId()));
        return new PetPublicVO(pet.getId(), pet.getName(), pet.getSpecies(), pet.getLevel(),
                pet.getGrowthStage(), pet.getPersonality(), achievementCount.intValue(), pet.getUserId());
    }

    @Override
    public Pet requireOwnedPet(Long userId) {
        Pet pet = stateService.findByUserId(userId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "你还没有宠物，先去领养一只吧");
        }
        stateService.applyIdleDecay(pet);
        return pet;
    }

    /** 今日剩余喂食次数；Redis 降级返回 null（Fail-Open 不限次，文档 §1.5） */
    Integer feedRemainingToday(Long userId) {
        try {
            String key = String.format(KEY_FEED_COUNTER, userId, LocalDate.now(ZoneId.of("UTC")));
            String used = redisTemplate.opsForValue().get(key);
            if (used == null) {
                return null;
            }
            return Math.max(0, FEED_DAILY_LIMIT - Integer.parseInt(used));
        } catch (Exception e) {
            log.warn("喂食日计数查询降级（Fail-Open）: userId={}", userId, e);
            return null;
        }
    }

    private PetVO toVo(Pet pet, Integer feedRemaining) {
        // 活动 authority 在 pet_activity（status 快照列仅展示冗余）
        PetActivity active = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, pet.getUserId())
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        String claimableType = null;
        if (active == null) {
            PetActivity claimable = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                    .eq(PetActivity::getUserId, pet.getUserId())
                    .eq(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                    .orderByDesc(PetActivity::getId)
                    .last("LIMIT 1"));
            if (claimable != null) {
                claimableType = claimable.getActivityType();
            }
        }
        String activeType = active != null ? active.getActivityType() : null;
        return new PetVO(
                pet.getId(), pet.getUserId(), pet.getName(), pet.getSpecies(), pet.getAppearance(),
                pet.getPersonality(), pet.getLevel(), pet.getExp(), stateService.expToNext(pet.getLevel()),
                pet.getGrowthStage(), pet.getHp(), pet.getMaxHp(), pet.getHunger(), pet.getHappiness(),
                pet.getEnergy(), pet.getCleanliness(), pet.getStrength(), pet.getIntelligence(),
                pet.getAgility(), pet.getCharm(),
                resolveStatus(pet, activeType), activeType,
                active != null ? active.getFinishedAt() : null, claimableType,
                pet.getIsPublic(), feedRemaining, pet.getLastStateUpdateAt());
    }

    private String resolveStatus(Pet pet, String activeType) {
        if (activeType != null) {
            return switch (PetActivityType.valueOf(activeType)) {
                case WORK -> PetStatus.WORKING.name();
                case STUDY -> PetStatus.STUDYING.name();
                case BOTTLE_FISHING -> PetStatus.FISHING.name();
                case REST, FEED, PLAY, CLEAN -> PetStatus.RESTING.name();
            };
        }
        return pet.getStatus() != null ? pet.getStatus() : PetStatus.IDLE.name();
    }
}
