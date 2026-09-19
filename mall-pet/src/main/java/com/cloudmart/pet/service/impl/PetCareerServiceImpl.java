package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.ApplyCareerRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetCareerConfig;
import com.cloudmart.pet.entity.PetCareerProgress;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetCareerConfigMapper;
import com.cloudmart.pet.repository.PetCareerProgressMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetCareerService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetActivityVO;
import com.cloudmart.pet.vo.PetCareerItemVO;
import com.cloudmart.pet.vo.PetCareerVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 宠物职业实现（三期）。
 *
 * <p>与打工的关系：共用 {@code pet_activity}（一次只能做一件事、服务端到点结算、CAS 领奖），
 * 但奖励按 {@code pet_career_config} 计算，并额外累计工作次数用于晋升；
 * 职业工作同时计入"打工"类每日任务（对用户而言它就是工作）。
 * 晋升顺序：<b>先本地改职业，再扣星光</b>——扣减失败整体回滚（与进化同一口径）。</p>
 */
@Service
@Slf4j
public class PetCareerServiceImpl implements PetCareerService {

    /** 职业工作每日次数上限（Redis 计数，Fail-Open 放行） */
    static final String KEY_CAREER_DAILY = "pet:ratelimit:career:%d:%s";
    /** COMPLETED 超过该小时数未领取 → 与打工一致由扫描器作废 */
    private static final int DEFAULT_TIER = 1;

    private final PetService petService;
    private final PetStateService stateService;
    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetCareerConfigMapper careerConfigMapper;
    private final PetCareerProgressMapper progressMapper;
    private final WishFeignClient wishFeignClient;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;
    private final PetDailyQuestService dailyQuestService;
    private final PetIntimacyService intimacyService;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;

    public PetCareerServiceImpl(PetService petService,
                                PetStateService stateService,
                                PetMapper petMapper,
                                PetActivityMapper activityMapper,
                                PetCareerConfigMapper careerConfigMapper,
                                PetCareerProgressMapper progressMapper,
                                WishFeignClient wishFeignClient,
                                PetAchievementService achievementService,
                                PetEventProducer eventProducer,
                                PetDailyQuestService dailyQuestService,
                                PetIntimacyService intimacyService,
                                PetProperties properties,
                                StringRedisTemplate redisTemplate) {
        this.petService = petService;
        this.stateService = stateService;
        this.petMapper = petMapper;
        this.activityMapper = activityMapper;
        this.careerConfigMapper = careerConfigMapper;
        this.progressMapper = progressMapper;
        this.wishFeignClient = wishFeignClient;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public PetCareerVO status(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        List<PetCareerConfig> configs = enabledConfigs();
        Map<String, PetCareerProgress> progressMap = progressMap(pet.getId());
        Map<String, PetCareerConfig> configMap = configs.stream()
                .collect(Collectors.toMap(PetCareerConfig::getCode, Function.identity(), (a, b) -> a));
        PetCareerConfig current = pet.getCareerCode() != null ? configMap.get(pet.getCareerCode()) : null;
        PetCareerProgress currentProgress = pet.getCareerCode() != null ? progressMap.get(pet.getCareerCode()) : null;

        List<PetCareerItemVO> items = configs.stream()
                .sorted(Comparator.comparing(PetCareerConfig::getCareerLine)
                        .thenComparing(c -> c.getTier() != null ? c.getTier() : DEFAULT_TIER))
                .map(config -> toItemVo(pet, config, configMap, progressMap))
                .toList();

        PetActivityVO active = activeCareerActivity(userId);
        boolean canPromote = current != null && promoteLockReason(pet, current, currentProgress) == null;
        PetCareerConfig promoteTo = current != null && current.getPromoteToCode() != null
                ? configMap.get(current.getPromoteToCode()) : null;
        List<PetCareerVO.HistoryItem> history = progressMap.values().stream()
                .filter(p -> p.getPromotedAt() != null)
                .sorted(Comparator.comparing(PetCareerProgress::getPromotedAt).reversed())
                .map(p -> new PetCareerVO.HistoryItem(p.getCareerCode(),
                        configMap.containsKey(p.getCareerCode())
                                ? configMap.get(p.getCareerCode()).getName() : p.getCareerCode(),
                        p.getWorkCount(), p.getTotalCurrency(), p.getStartedAt(), p.getPromotedAt()))
                .toList();

        return new PetCareerVO(
                pet.getCareerCode(),
                current != null ? current.getName() : null,
                current != null ? current.getCareerLine() : null,
                current != null ? current.getTier() : null,
                current != null ? current.getIcon() : null,
                currentProgress != null && currentProgress.getWorkCount() != null ? currentProgress.getWorkCount() : 0,
                active,
                canPromote,
                promoteTo != null ? promoteTo.getName() : null,
                current != null ? orZero(current.getPromoteRequiredCount()) : 0,
                current != null ? orZero(current.getPromoteStarCost()) : 0,
                current != null ? promoteLockReason(pet, current, currentProgress) : null,
                items, history);
    }

    @Override
    @Transactional
    public PetCareerItemVO apply(Long userId, ApplyCareerRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetCareerConfig config = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, request.careerCode())
                .last("LIMIT 1"));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_NOT_FOUND, "这个职业暂时不招人");
        }
        if (config.getCode().equals(pet.getCareerCode())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED, "已经在做这份工作啦");
        }
        Map<String, PetCareerConfig> configMap = enabledConfigs().stream()
                .collect(Collectors.toMap(PetCareerConfig::getCode, Function.identity(), (a, b) -> a));
        // 高阶职业只能由同路线上一阶晋升而来，不能直接入职
        if (orOne(config.getTier()) > 1) {
            PetCareerConfig previous = pet.getCareerCode() != null ? configMap.get(pet.getCareerCode()) : null;
            if (previous == null || !config.getCode().equals(previous.getPromoteToCode())) {
                throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED,
                        "「" + config.getName() + "」需要先在同路线晋升才能担任");
            }
        }
        String lockReason = applyLockReason(pet, config);
        if (lockReason != null) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED, lockReason);
        }
        String previousCode = pet.getCareerCode();
        keepProgress(pet, config.getCode());
        pet.setCareerCode(config.getCode());
        pet.setStatus(PetStatus.IDLE.name());
        petMapper.updateById(pet);
        eventProducer.publish(RocketMQConfig.PET_TAG_CAREER, new PetEventProducer.PetEventMessage(
                userId, "PET_CAREER_PROMOTED",
                "我入职啦！",
                pet.getName() + "：主人，我现在是「" + config.getName() + "」啦，明天开始好好上班～",
                pet.getId(), "PET_CAREER_PROMOTED"));
        log.info("宠物入职: userId={}, petId={}, from={}, to={}", userId, pet.getId(), previousCode, config.getCode());
        return toItemVo(pet, config, configMap, progressMap(pet.getId()));
    }

    @Override
    @Transactional
    public PetActivityVO startWork(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetCareerConfig config = requireCurrentCareer(pet);
        ensureNoBusyActivity(userId);
        if (pet.getLevel() < orOne(config.getRequiredLevel())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_LOCKED,
                    "等级达到 Lv." + config.getRequiredLevel() + " 才能做这份工作");
        }
        if (pet.getEnergy() < orZero(config.getEnergyCost())) {
            throw new BusinessException(PetErrorCodes.PET_ENERGY_INSUFFICIENT, "宠物没有力气了，先休息一下吧");
        }
        if (pet.getHunger() < orZero(config.getHungerCost())) {
            throw new BusinessException(PetErrorCodes.PET_HUNGER_TOO_LOW, "肚子太空了干不动活，先喂点东西吧");
        }
        requireDailyQuota(userId);

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(userId);
        activity.setActivityType(PetActivityType.CAREER_WORK.name());
        activity.setConfigId(config.getId());
        activity.setStatus(PetActivityStatus.IN_PROGRESS.name());
        activity.setStartedAt(now);
        activity.setFinishedAt(now.plusSeconds(config.getDurationSeconds() != null ? config.getDurationSeconds() : 0));
        try {
            activityMapper.insert(activity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT, "宠物已经在忙另一件事啦");
        }
        pet.setEnergy(Math.max(0, pet.getEnergy() - orZero(config.getEnergyCost())));
        pet.setHunger(Math.max(0, pet.getHunger() - orZero(config.getHungerCost())));
        pet.setStatus(PetStatus.WORKING.name());
        petMapper.updateById(pet);
        return toActivityVo(activity, config.getName());
    }

    @Override
    @Transactional
    public PetActivityVO claimWork(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetActivity activity = requireClaimableActivity(userId);
        PetCareerConfig config = careerConfigMapper.selectById(activity.getConfigId());
        if (config == null) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_NOT_FOUND, "这个职业已经下架了");
        }
        int claimed = activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                .set(PetActivity::getStatus, PetActivityStatus.CLAIMED.name())
                .set(PetActivity::getClaimedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetActivity::getId, activity.getId())
                .in(PetActivity::getStatus,
                        PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name()));
        if (claimed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED, "奖励已经领取过啦");
        }
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        int expReward = orZero(config.getExpReward());
        int currencyReward = orZero(config.getCurrencyReward());
        int intelligenceBonus = PetActivityServiceImpl.intelligenceBonusPercent(pet.getIntelligence());
        expReward += Math.round(expReward * intelligenceBonus / 100f);
        currencyReward += Math.round(currencyReward * intelligenceBonus / 100f);
        // 亲密度先叠加（与经验同一次乐观锁写入）
        intimacyService.gain(pet, PetIntimacySource.WORK);
        int levelups = stateService.grantExp(pet, expReward);
        if (currencyReward > 0) {
            wishFeignClient.earnStarlight(userId, currencyReward, activity.getId());
        }
        accumulateProgress(pet, config.getCode(), currencyReward);
        activity.setResult(PetJsonUtils.toJson(Map.of(
                "exp", expReward, "currency", currencyReward, "intelligenceBonus", intelligenceBonus,
                "careerCode", config.getCode())));
        activityMapper.updateById(activity);
        // 每日任务：职业工作同时计入"打工"口径
        dailyQuestService.record(pet, PetQuestType.WORK, 1);
        dailyQuestService.record(pet, PetQuestType.CAREER_WORK, 1);
        achievementService.evaluate(pet, PetAchievementService.Event.WORK_CLAIMED);
        if (levelups > 0) {
            achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
            eventProducer.publish(RocketMQConfig.PET_TAG_LEVEL_UP, new PetEventProducer.PetEventMessage(
                    userId, "PET_LEVEL_UP", "宠物升级啦！",
                    pet.getName() + " 升到了 Lv." + pet.getLevel() + "，快去看看它吧！",
                    pet.getId(), "PET_LEVEL_UP"));
        }
        return toActivityVo(activity, config.getName());
    }

    @Override
    @Transactional
    public PetCareerItemVO promote(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetCareerConfig current = requireCurrentCareer(pet);
        PetCareerProgress progress = progressMap(pet.getId()).get(current.getCode());
        String lockReason = promoteLockReason(pet, current, progress);
        if (lockReason != null) {
            if (current.getPromoteToCode() == null) {
                throw new BusinessException(PetErrorCodes.PET_CAREER_MAX_TIER, "已经是这条路线最高阶啦");
            }
            throw new BusinessException(PetErrorCodes.PET_CAREER_PROMOTE_REQUIRED, lockReason);
        }
        PetCareerConfig target = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, current.getPromoteToCode())
                .last("LIMIT 1"));
        if (target == null || !Boolean.TRUE.equals(target.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_NOT_FOUND, "晋升目标职业暂时不可用");
        }
        // 先本地晋升（写职业 + 关闭旧进度），再扣星光；扣减失败整体回滚（与进化同一口径）
        pet.setCareerCode(target.getCode());
        pet.setStatus(PetStatus.IDLE.name());
        petMapper.updateById(pet);
        closureProgress(pet, current.getCode());
        keepProgress(pet, target.getCode());
        int cost = orZero(current.getPromoteStarCost());
        if (cost > 0) {
            wishFeignClient.spendStarlight(userId, cost, pet.getId());
        }
        eventProducer.publish(RocketMQConfig.PET_TAG_CAREER, new PetEventProducer.PetEventMessage(
                userId, "PET_CAREER_PROMOTED",
                "我晋升啦！",
                pet.getName() + "：主人，我晋升成「" + target.getName() + "」啦，以后能赚更多小钱钱！",
                pet.getId(), "PET_CAREER_PROMOTED"));
        achievementService.evaluate(pet, PetAchievementService.Event.LEVEL_UP);
        Map<String, PetCareerConfig> configMap = enabledConfigs().stream()
                .collect(Collectors.toMap(PetCareerConfig::getCode, Function.identity(), (a, b) -> a));
        return toItemVo(pet, target, configMap, progressMap(pet.getId()));
    }

    // ---------------- 内部 ----------------

    private List<PetCareerConfig> enabledConfigs() {
        return careerConfigMapper.selectList(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getEnabled, true)
                .orderByAsc(PetCareerConfig::getSort));
    }

    private PetCareerConfig requireCurrentCareer(Pet pet) {
        if (pet.getCareerCode() == null || pet.getCareerCode().isBlank()) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_REQUIRED, "先找一份工作入职吧");
        }
        PetCareerConfig config = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, pet.getCareerCode())
                .last("LIMIT 1"));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_CAREER_NOT_FOUND, "当前职业已下架，换一份吧");
        }
        return config;
    }

    private Map<String, PetCareerProgress> progressMap(Long petId) {
        return progressMapper.selectList(new LambdaQueryWrapper<PetCareerProgress>()
                        .eq(PetCareerProgress::getPetId, petId))
                .stream()
                .collect(Collectors.toMap(PetCareerProgress::getCareerCode, Function.identity(), (a, b) -> a));
    }

    /** 入职/晋升到某职业：进度行不存在则新建（uk 幂等），已关闭的历史行重新开启 */
    private void keepProgress(Pet pet, String careerCode) {
        PetCareerProgress existing = progressMapper.selectOne(new LambdaQueryWrapper<PetCareerProgress>()
                .eq(PetCareerProgress::getPetId, pet.getId())
                .eq(PetCareerProgress::getCareerCode, careerCode)
                .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getPromotedAt() != null) {
                progressMapper.update(null, new LambdaUpdateWrapper<PetCareerProgress>()
                        .set(PetCareerProgress::getPromotedAt, null)
                        .eq(PetCareerProgress::getId, existing.getId()));
            }
            return;
        }
        PetCareerProgress progress = new PetCareerProgress();
        progress.setPetId(pet.getId());
        progress.setUserId(pet.getUserId());
        progress.setCareerCode(careerCode);
        progress.setWorkCount(0);
        progress.setTotalCurrency(0);
        progress.setStartedAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            progressMapper.insert(progress);
        } catch (DuplicateKeyException e) {
            log.debug("职业进度并发生成，忽略: petId={}, code={}", pet.getId(), careerCode);
        }
    }

    /** 晋升离开旧职业：写 promotedAt（保留次数用于历史展示） */
    private void closureProgress(Pet pet, String careerCode) {
        progressMapper.update(null, new LambdaUpdateWrapper<PetCareerProgress>()
                .set(PetCareerProgress::getPromotedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(PetCareerProgress::getPetId, pet.getId())
                .eq(PetCareerProgress::getCareerCode, careerCode)
                .isNull(PetCareerProgress::getPromotedAt));
    }

    /** 工作奖励累计：次数 + 星光（原子 UPDATE，幂等由领奖 CAS 保证只加一次） */
    private void accumulateProgress(Pet pet, String careerCode, int currencyReward) {
        progressMapper.update(null, new LambdaUpdateWrapper<PetCareerProgress>()
                .setSql("work_count = work_count + 1")
                .setSql("total_currency = total_currency + " + Math.max(0, currencyReward))
                .eq(PetCareerProgress::getPetId, pet.getId())
                .eq(PetCareerProgress::getCareerCode, careerCode));
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

    /** 职业工作每日次数（Redis，Fail-Open 放行） */
    private void requireDailyQuota(Long userId) {
        try {
            String key = String.format(KEY_CAREER_DAILY, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            int limit = properties.getCareer().getDailyWorkLimit();
            if (used != null && used > limit) {
                throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED,
                        "今天已经工作 " + limit + " 次啦，明天再接着干吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("职业工作限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
        }
    }

    /** 可领取的职业工作（IN_PROGRESS 已到点则惰性流转为 COMPLETED） */
    private PetActivity requireClaimableActivity(Long userId) {
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.CAREER_WORK.name())
                .in(PetActivity::getStatus,
                        PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FOUND, "没有可领取的工作");
        }
        if (PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            if (activity.getFinishedAt().isAfter(LocalDateTime.now(ZoneId.of("UTC")))) {
                throw new BusinessException(PetErrorCodes.PET_ACTIVITY_NOT_FINISHED, "工作还没做完，再等等吧");
            }
            activityMapper.update(null, new LambdaUpdateWrapper<PetActivity>()
                    .set(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())
                    .eq(PetActivity::getId, activity.getId())
                    .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name()));
            activity.setStatus(PetActivityStatus.COMPLETED.name());
        }
        return activity;
    }

    private PetActivityVO activeCareerActivity(Long userId) {
        PetActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.CAREER_WORK.name())
                .in(PetActivity::getStatus,
                        PetActivityStatus.IN_PROGRESS.name(), PetActivityStatus.COMPLETED.name())
                .orderByDesc(PetActivity::getId)
                .last("LIMIT 1"));
        if (activity == null) {
            return null;
        }
        PetCareerConfig config = careerConfigMapper.selectById(activity.getConfigId());
        return toActivityVo(activity, config != null ? config.getName() : null);
    }

    private PetActivityVO toActivityVo(PetActivity activity, String configName) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        boolean inProgress = PetActivityStatus.IN_PROGRESS.name().equals(activity.getStatus());
        long remaining = inProgress
                ? Math.max(0, java.time.Duration.between(now, activity.getFinishedAt()).getSeconds())
                : 0;
        boolean canClaim = PetActivityStatus.COMPLETED.name().equals(activity.getStatus())
                || (inProgress && !activity.getFinishedAt().isAfter(now));
        return new PetActivityVO(activity.getId(), activity.getActivityType(), activity.getConfigId(),
                configName, activity.getStatus(), activity.getStartedAt(), activity.getFinishedAt(),
                remaining, canClaim, activity.getClaimedAt(), activity.getResult());
    }

    private PetCareerItemVO toItemVo(Pet pet, PetCareerConfig config,
                                     Map<String, PetCareerConfig> configMap,
                                     Map<String, PetCareerProgress> progressMap) {
        PetCareerProgress progress = progressMap.get(config.getCode());
        boolean current = config.getCode().equals(pet.getCareerCode());
        String lockReason = applyLockReason(pet, config);
        PetCareerConfig promoteTo = config.getPromoteToCode() != null
                ? configMap.get(config.getPromoteToCode()) : null;
        boolean canPromote = current && promoteLockReason(pet, config, progress) == null
                && config.getPromoteToCode() != null;
        return new PetCareerItemVO(
                config.getCode(), config.getName(), config.getDescription(), config.getCareerLine(),
                config.getTier() != null ? config.getTier() : DEFAULT_TIER, config.getIcon(),
                config.getRequiredLevel(), config.getRequiredIntelligence(),
                config.getDurationSeconds(), config.getEnergyCost(), config.getHungerCost(),
                config.getExpReward(), config.getCurrencyReward(),
                progress != null && progress.getWorkCount() != null ? progress.getWorkCount() : 0,
                current, current || lockReason == null, current ? null : lockReason,
                promoteTo != null ? promoteTo.getName() : null,
                orZero(config.getPromoteRequiredCount()), orZero(config.getPromoteStarCost()),
                progress != null && progress.getWorkCount() != null ? progress.getWorkCount() : 0,
                canPromote,
                current ? promoteLockReason(pet, config, progress) : null);
    }

    private String applyLockReason(Pet pet, PetCareerConfig config) {
        if (pet.getLevel() < orOne(config.getRequiredLevel())) {
            return "需要 Lv." + config.getRequiredLevel();
        }
        if (pet.getIntelligence() < orZero(config.getRequiredIntelligence())) {
            return "需要智力 " + config.getRequiredIntelligence();
        }
        return null;
    }

    /** 晋升条件说明（null = 满足） */
    private String promoteLockReason(Pet pet, PetCareerConfig config, PetCareerProgress progress) {
        if (config.getPromoteToCode() == null || config.getPromoteToCode().isBlank()) {
            return "已是最高阶";
        }
        int required = orZero(config.getPromoteRequiredCount());
        int done = progress != null && progress.getWorkCount() != null ? progress.getWorkCount() : 0;
        if (done < required) {
            return "还需工作 " + (required - done) + " 次";
        }
        PetCareerConfig target = careerConfigMapper.selectOne(new LambdaQueryWrapper<PetCareerConfig>()
                .eq(PetCareerConfig::getCode, config.getPromoteToCode())
                .last("LIMIT 1"));
        if (target != null && pet.getLevel() < orOne(target.getRequiredLevel())) {
            return "晋升需要 Lv." + target.getRequiredLevel();
        }
        return null;
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static int orOne(Integer value) {
        return value != null ? value : 1;
    }
}
