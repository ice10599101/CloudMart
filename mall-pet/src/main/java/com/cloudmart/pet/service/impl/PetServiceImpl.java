package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.CreatePetRequest;
import com.cloudmart.pet.dto.RenamePetRequest;
import com.cloudmart.pet.dto.UpdateAppearanceRequest;
import com.cloudmart.pet.dto.UpdatePrivacyRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetAchievementRecord;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetCareerConfig;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetGender;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.repository.PetAchievementRecordMapper;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetCareerConfigMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetReminderService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetIntimacyMath;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetPublicVO;
import com.cloudmart.pet.vo.PetSummaryVO;
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
import java.util.List;
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

    static final long RENAME_COOLDOWN_DAYS = 30;

    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetAchievementRecordMapper achievementRecordMapper;
    private final PetStateService stateService;
    private final PetReminderService reminderService;
    private final StringRedisTemplate redisTemplate;
    private final PetProperties properties;
    private final PetCareerConfigMapper careerConfigMapper;

    public PetServiceImpl(PetMapper petMapper,
                          PetActivityMapper activityMapper,
                          PetAchievementRecordMapper achievementRecordMapper,
                          PetStateService stateService,
                          PetReminderService reminderService,
                          StringRedisTemplate redisTemplate,
                          PetProperties properties,
                          PetCareerConfigMapper careerConfigMapper,
                          com.cloudmart.pet.repository.PetInventoryMapper skinInventoryMapper,
                          PetCompanionFeatureService companionFeatureService) {
        this.petMapper = petMapper;
        this.activityMapper = activityMapper;
        this.achievementRecordMapper = achievementRecordMapper;
        this.stateService = stateService;
        this.reminderService = reminderService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.careerConfigMapper = careerConfigMapper;
        this.skinInventoryMapper = skinInventoryMapper;
        this.companionFeatureService = companionFeatureService;
    }

    /** 背包 Mapper（B12：手动改外观同步卸皮肤穿戴标记） */
    private final com.cloudmart.pet.repository.PetInventoryMapper skinInventoryMapper;
    private final PetCompanionFeatureService companionFeatureService;

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
        // B03：用户级原子配额——先对已有宠物行加锁（FOR UPDATE 锁住 idx_pet_user 范围，
        // 并发领养在范围间隙上互斥），再检查数量，防止上限前同时领养超限
        long owned = petMapper.selectCount(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .last("FOR UPDATE"));
        if (owned >= properties.getMultiPet().getMaxPets()) {
            throw new BusinessException(PetErrorCodes.PET_PET_LIMIT_REACHED,
                    "最多只能养 " + properties.getMultiPet().getMaxPets() + " 只宠物，先陪陪它们吧");
        }
        boolean first = owned == 0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        Pet pet = new Pet();
        pet.setUserId(userId);
        pet.setName(request.name().trim());
        pet.setSpecies(request.species());
        // 性别：领养可选，未传（旧客户端）默认男
        pet.setGender(request.gender() != null ? request.gender() : PetGender.MALE.name());
        pet.setAppearance(PetJsonUtils.toJson(Map.of(
                "color", request.color() != null ? request.color() : "orange",
                "accessory", request.accessory() != null ? request.accessory() : "none")));
        pet.setPersonality(request.personality());
        pet.setLevel(1);
        pet.setExp(0);
        pet.setGrowthStage(stateService.growthStageFor(1));
        pet.setEvolutionStage(0);
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
        // 第一只自动成为主宠；后续领养的宠物需手动切换（原文档 §37.1 主宠物语义）
        pet.setIsActive(first);
        pet.setLastStateUpdateAt(now);
        try {
            petMapper.insert(pet);
        } catch (DuplicateKeyException e) {
            // 并发领养：uk_pet_user_active（唯一主宠）数据库层兜底
            throw new BusinessException(PetErrorCodes.PET_ALREADY_EXISTS, "宠物创建冲突了，请稍后再试");
        }
        if (first) {
            // N01：首只宠物赠送基础家具（每用户一次，操作键幂等，重试不重复入包）
            try {
                companionFeatureService.grantStarterFurniture(userId);
            } catch (Exception e) {
                log.warn("新手家具赠送失败（不阻断领养）: userId={}", userId, e);
            }
        }
        return toVo(pet, null);
    }

    @Override
    public List<PetSummaryVO> listPets(Long userId) {
        List<Pet> pets = stateService.listByUserId(userId);
        if (pets.isEmpty()) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "你还没有宠物，先去领养一只吧");
        }
        return pets.stream().map(PetServiceImpl::toSummary).toList();
    }

    @Override
    @Transactional
    public PetSummaryVO activatePet(Long userId, Long petId) {
        Pet target = petMapper.selectById(petId);
        if (target == null || !userId.equals(target.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能切换自己的宠物");
        }
        if (Boolean.TRUE.equals(target.getIsActive())) {
            return toSummary(target);
        }
        // 先释放原主宠再占用目标主宠：uk_pet_user_active 保证并发下不会出现双主宠
        petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .set(Pet::getIsActive, false)
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true));
        int updated = petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .set(Pet::getIsActive, true)
                .eq(Pet::getId, petId)
                .eq(Pet::getUserId, userId));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "只能切换自己的宠物");
        }
        target.setIsActive(true);
        return toSummary(target);
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
        // 手动改外观视为脱离皮肤：卸下穿戴中的皮肤，避免"皮肤标记"与"实际外观"不一致
        // B12：手动改外观按明确接口意图卸皮肤，并同步背包穿戴标记（不因改名等无关保存误触发）
        skinInventoryMapper.update(null, new LambdaUpdateWrapper<com.cloudmart.pet.entity.PetInventory>()
                .set(com.cloudmart.pet.entity.PetInventory::getEquipped, false)
                .eq(com.cloudmart.pet.entity.PetInventory::getPetId, pet.getId())
                .eq(com.cloudmart.pet.entity.PetInventory::getItemType, com.cloudmart.pet.enums.PetItemType.SKIN.name()));
        pet.setBaseAppearance(pet.getAppearance());
        pet.setSkinCode(null);
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
        return new PetPublicVO(pet.getId(), pet.getName(), pet.getSpecies(), pet.getGender(), pet.getLevel(),
                pet.getGrowthStage(), pet.getPersonality(), achievementCount.intValue(), pet.getUserId());
    }

    @Override
    public Pet requireOwnedPet(Long userId) {
        return stateService.requireActivePet(userId);
    }

    /** 今日剩余喂食次数；Redis 降级返回 null（Fail-Open 不限次，文档 §1.5） */
    Integer feedRemainingToday(Long userId) {
        try {
            String key = String.format(KEY_FEED_COUNTER, userId, LocalDate.now(ZoneId.of("UTC")));
            String used = redisTemplate.opsForValue().get(key);
            if (used == null) {
                return null;
            }
            return Math.max(0, properties.getInteraction().getFeedDailyLimit() - Integer.parseInt(used));
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
                pet.getId(), pet.getUserId(), pet.getName(), pet.getSpecies(), pet.getGender(),
                pet.getAppearance(),
                pet.getPersonality(), pet.getLevel(), pet.getExp(), stateService.expToNext(pet.getLevel()),
                pet.getGrowthStage(), pet.getHp(), pet.getMaxHp(), pet.getHunger(), pet.getHappiness(),
                pet.getEnergy(), pet.getCleanliness(), pet.getStrength(), pet.getIntelligence(),
                pet.getAgility(), pet.getCharm(),
                resolveStatus(pet, activeType), activeType,
                active != null ? active.getFinishedAt() : null, claimableType,
                pet.getIsPublic(), feedRemaining, pet.getLastStateUpdateAt(),
                pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0, pet.getSkinCode(),
                (int) stateService.countByUserId(pet.getUserId()), properties.getMultiPet().getMaxPets(),
                // 三期：亲密度/陪伴（公式来自 PetIntimacyMath，避免与亲密度服务循环依赖）
                intimacyOf(pet),
                PetIntimacyMath.levelOf(intimacyOf(pet), properties.getIntimacy().getLevelThresholds()),
                PetIntimacyMath.levelName(
                        PetIntimacyMath.levelOf(intimacyOf(pet), properties.getIntimacy().getLevelThresholds()),
                        properties.getIntimacy().getLevelNames()),
                PetIntimacyMath.toNext(intimacyOf(pet), properties.getIntimacy().getLevelThresholds()),
                PetIntimacyMath.expBonusPercent(pet, properties.getIntimacy()),
                pet.getCompanionSeconds() != null ? pet.getCompanionSeconds() : 0L,
                pet.getTodayCompanionSeconds() != null ? pet.getTodayCompanionSeconds() : 0,
                pet.getCompanionDays() != null ? pet.getCompanionDays() : 0,
                pet.getCompanionStreak() != null ? pet.getCompanionStreak() : 0,
                pet.getCareerCode(), careerNameOf(pet.getCareerCode()), careerTierOf(pet.getCareerCode()));
    }

    private int intimacyOf(Pet pet) {
        return pet.getIntimacy() != null ? pet.getIntimacy() : 0;
    }

    /** 当前职业名（未入职或配置已删返回 null；展示型数据静默降级） */
    private String careerNameOf(String careerCode) {
        PetCareerConfig config = careerConfigOf(careerCode);
        return config != null ? config.getName() : null;
    }

    private Integer careerTierOf(String careerCode) {
        PetCareerConfig config = careerConfigOf(careerCode);
        return config != null ? config.getTier() : null;
    }

    private PetCareerConfig careerConfigOf(String careerCode) {
        if (careerCode == null || careerCode.isBlank()) {
            return null;
        }
        try {
            return careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                    .eq(PetCareerConfig::getCode, careerCode)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("职业配置查询降级: careerCode={}", careerCode, e);
            return null;
        }
    }

    /** 多宠物列表项（不触发懒更新落库，列表只做展示；主宠状态以 PetVO 为准） */
    private static PetSummaryVO toSummary(Pet pet) {
        return new PetSummaryVO(pet.getId(), pet.getName(), pet.getSpecies(), pet.getGender(), pet.getAppearance(),
                pet.getPersonality(), pet.getLevel(), pet.getGrowthStage(),
                pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0, pet.getSkinCode(),
                pet.getHp(), pet.getMaxHp(), pet.getHunger(), pet.getHappiness(),
                pet.getEnergy(), pet.getCleanliness(), pet.getIsActive());
    }

    private String resolveStatus(Pet pet, String activeType) {
        if (activeType != null) {
            return switch (PetActivityType.valueOf(activeType)) {
                case WORK, CAREER_WORK -> PetStatus.WORKING.name();
                case STUDY -> PetStatus.STUDYING.name();
                case BOTTLE_FISHING -> PetStatus.FISHING.name();
                case REST, FEED, PLAY, CLEAN, VISIT, EVOLVE -> PetStatus.RESTING.name();
            };
        }
        return pet.getStatus() != null ? pet.getStatus() : PetStatus.IDLE.name();
    }
}
