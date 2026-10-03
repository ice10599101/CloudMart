package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
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
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetGender;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.repository.PetAchievementRecordMapper;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetCareerConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetReminderService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetIntimacyMath;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetPublicVO;
import com.cloudmart.pet.vo.PetSummaryVO;
import com.cloudmart.pet.vo.PetVO;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PetServiceImpl implements PetService {

    /** Redis Key：宠物模块限频计数，规范 {service}:{module}:{type}:{id}:{date} */

    /** Redis Key：领养互斥锁（P1-1：首只宠物无可锁行时由它兜底并发） */
    static final String KEY_ADOPT_LOCK = "pet:lock:adopt:%d";

    static final long RENAME_COOLDOWN_DAYS = 30;

    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetAchievementRecordMapper achievementRecordMapper;
    private final PetStateService stateService;
    private final PetReminderService reminderService;
    private final StringRedisTemplate redisTemplate;
    private final PetProperties properties;
    private final PetCareerConfigMapper careerConfigMapper;
    /** 背包 Mapper（B12：手动改外观同步卸皮肤穿戴标记） */
    private final PetInventoryMapper skinInventoryMapper;
    private final PetCompanionFeatureService companionFeatureService;
    private final PetContentSafetyService safetyService;
    private final PetRankingCache rankingCache;
    private final PetQuotaService quotaService;
    private final PetClock petClock;

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
        // P0-1 内容安全：宠物名先过敏感词（命中拒绝，词库故障 Fail-Open 放行）
        safetyService.requireCleanPetName(request.name().trim());
        // P1-1：领养互斥——Redis 锁兜底"首只宠物无可锁行"的并发窗口（Fail-Open 退化为行锁）；
        // 已有宠物时 SELECT id ... FOR UPDATE 真实行锁串行化（原 COUNT FOR UPDATE 快照读不产生锁）
        String lockKey = String.format(KEY_ADOPT_LOCK, userId);
        boolean locked = false;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
            locked = Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("领养分布式锁不可用（Fail-Open，退化行锁）: userId={}", userId, e);
        }
        if (!locked) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "领养处理中，请稍后再试");
        }
        try {
            return doCreatePet(userId, request);
        } finally {
            try {
                redisTemplate.delete(lockKey);
            } catch (Exception e) {
                log.debug("领养锁释放失败（TTL 兜底）: userId={}", userId);
            }
        }
    }

    /** 领养主体：行锁内检查数量上限并落库（调用方持有领养锁） */
    private PetVO doCreatePet(Long userId, CreatePetRequest request) {
        // P1-1：锁定该用户已有宠物行（真实行锁），并发领养在行锁上串行；
        // 首只（空结果）由领养锁兜底，uk_pet_user_active + DuplicateKey 最终兜底
        long owned = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                        .select(Pet::getId)
                        .eq(Pet::getUserId, userId)
                        .last("FOR UPDATE"))
                .size();
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
                // 未传颜色时回落种类默认色（西瓜绿/蓝莓蓝等），与 PetItemCatalog.SPECIES_DEFAULT_COLOR 保持一致
                "color", request.color() != null ? request.color() : PetItemCatalog.defaultColorFor(request.species()),
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
        // P1-4：新宠物立即进入等级榜缓存（Level 1 / exp 0），不等每日重建
        rankingCache.onPetCreated(pet.getId(), Boolean.TRUE.equals(pet.getIsPublic()));
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
        // P0-1 内容安全：新名字先过敏感词
        safetyService.requireCleanPetName(request.name().trim());
        pet.setName(request.name().trim());
        pet.setLastRenamedAt(now);
        // P1-2：乐观锁冲突必须显式失败（静默吞掉会让用户误以为改名成功）
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "操作冲突，请刷新后重试");
        }
        return toVo(pet, feedRemainingToday(userId));
    }

    @Override
    @Transactional
    public PetVO setOwnerTitle(Long userId, String ownerTitle) {
        Pet pet = requireOwnedPet(userId);
        // blank = 重置默认；其余经内容安全校验（称呼会进入 AI prompt 与提醒文案，必须过滤）
        String title = ownerTitle == null || ownerTitle.isBlank()
                ? "主人" : ownerTitle.strip();
        if (!"主人".equals(title)) {
            if (title.length() > 12) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "称呼最多 12 个字");
            }
            safetyService.check(title).ifPresent(hit -> {
                throw new BusinessException(PetErrorCodes.PET_OWNER_TITLE_SENSITIVE,
                        "这个称呼不太合适，换一个吧");
            });
        }
        // 条件更新（owner+id），乐观锁版本随 @Version 前进
        int updated = petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .set(Pet::getOwnerTitle, title)
                .eq(Pet::getId, pet.getId())
                .eq(Pet::getUserId, userId));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "操作冲突，请刷新后重试");
        }
        pet.setOwnerTitle(title);
        return toVo(pet, feedRemainingToday(userId));
    }

    @Override
    @Transactional
    public PetVO updateAppearance(Long userId, UpdateAppearanceRequest request) {
        Pet pet = requireOwnedPet(userId);
        pet.setAppearance(PetJsonUtils.toJson(Map.of("color", request.color(), "accessory", request.accessory())));
        // 手动改外观视为脱离皮肤：卸下穿戴中的皮肤，避免"皮肤标记"与"实际外观"不一致
        // B12：手动改外观按明确接口意图卸皮肤，并同步背包穿戴标记（不因改名等无关保存误触发）
        skinInventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                .set(PetInventory::getEquipped, false)
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, PetItemType.SKIN.name()));
        pet.setBaseAppearance(pet.getAppearance());
        pet.setSkinCode(null);
        // P1-2：乐观锁冲突必须显式失败（与 rest() 同一契约）
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "操作冲突，请刷新后重试");
        }
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

    /**
     * 今日剩余喂食次数（R20）：与 feed 动作同源（PetQuotaService 的 DB 配额+businessDate），
     * 不再读旧 Redis UTC 日计数——两条口径并存时关 Redis 会显示"不限次"/计数漂移。
     */
    Integer feedRemainingToday(Long userId) {
        return quotaService.remaining(userId, PetQuotaService.QuotaType.FEED, 0,
                properties.getInteraction().getFeedDailyLimit());
    }

    /** P2-1：互动链路复用入口——基于调用方事务内已同步状态的实体组装 VO，免二次全量查询 */
    @Override
    public PetVO toVo(Pet pet) {
        return toVo(pet, feedRemainingToday(pet.getUserId()));
    }

    private PetVO toVo(Pet pet, Integer feedRemaining) {
        // P2-1：进行中 + 可领取活动合并为一次查询（原两条 SELECT），按 id 降序取每状态最新一条。
        // R30：按 petId 过滤——活动归属开工宠物，A 在忙时 B 的 VO 不再显示 A 的活动（账号忙碌归属属账号级展示，由活动中心接口承载）
        List<PetActivity> recentActivities = activityMapper.selectList(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getPetId, pet.getId())
                .in(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 4"));
        PetActivity active = null;
        PetActivity claimable = null;
        for (PetActivity activity : recentActivities) {
            if (active == null && PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
                active = activity;
            }
            if (claimable == null && PetActivityStatus.COMPLETED.name().equals(activity.getStatus())) {
                claimable = activity;
            }
        }
        String claimableType = claimable != null ? claimable.getActivityType() : null;
        String activeType = active != null ? active.getActivityType() : null;
        // P2-4：亲密度只计算一次（原表达式链内重复调用 5 次）
        int intimacy = intimacyOf(pet);
        int intimacyLevel = PetIntimacyMath.levelOf(intimacy, properties.getIntimacy().getLevelThresholds());
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
                intimacy,
                intimacyLevel,
                PetIntimacyMath.levelName(intimacyLevel, properties.getIntimacy().getLevelNames()),
                PetIntimacyMath.toNext(intimacy, properties.getIntimacy().getLevelThresholds()),
                PetIntimacyMath.expBonusPercent(pet, properties.getIntimacy()),
                pet.getCompanionSeconds() != null ? pet.getCompanionSeconds() : 0L,
                pet.getTodayCompanionSeconds() != null ? pet.getTodayCompanionSeconds() : 0,
                pet.getCompanionDays() != null ? pet.getCompanionDays() : 0,
                pet.getCompanionStreak() != null ? pet.getCompanionStreak() : 0,
                pet.getCareerCode(), careerNameOf(pet.getCareerCode()), careerTierOf(pet.getCareerCode()),
                ownerTitleOf(pet),
                // R20：业务日重置点（JacksonConfig 统一输出 RFC3339 UTC 带 Z），客户端倒计时以服务端为准
                petClock.nextBusinessResetUtc());
    }

    /** 主人称呼（宠物对主人的叫法；未设置回落「主人」） */
    static String ownerTitleOf(Pet pet) {
        return pet.getOwnerTitle() != null && !pet.getOwnerTitle().isBlank()
                ? pet.getOwnerTitle() : "主人";
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

    /** 职业配置本地 TTL 缓存条目（P2-1：toVo 每次调用查 2 次配置表，缓存后 5 分钟内零 DB 往返） */
    private record CareerCacheEntry(PetCareerConfig config, long expiresAtNanos) {
    }

    /** 职业配置缓存（TTL 5 分钟；配置由管理端低频维护，短陈旧可接受，负结果同样缓存防穿透） */
    private static final long CAREER_CACHE_TTL_NANOS = 5 * 60L * 1_000_000_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, CareerCacheEntry> careerConfigCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private PetCareerConfig careerConfigOf(String careerCode) {
        if (careerCode == null || careerCode.isBlank()) {
            return null;
        }
        long now = System.nanoTime();
        CareerCacheEntry cached = careerConfigCache.get(careerCode);
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.config();
        }
        PetCareerConfig config = null;
        try {
            config = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                    .eq(PetCareerConfig::getCode, careerCode)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("职业配置查询降级: careerCode={}", careerCode, e);
        }
        careerConfigCache.put(careerCode, new CareerCacheEntry(config, now + CAREER_CACHE_TTL_NANOS));
        return config;
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
