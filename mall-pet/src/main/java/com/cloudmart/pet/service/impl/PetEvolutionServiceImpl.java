package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetEvolutionConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetEvolutionConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetEvolutionService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetEvolutionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 宠物进化实现。
 *
 * <p>顺序：<b>先应用进化（本地），再扣星光</b>——扣减失败（余额不足 402 / 服务降级 503）
 * 回滚本地事务，不会出现"星光扣了但没进化"。进化链取自配置表，禁止硬编码阶段数值。</p>
 */
@Service
@Slf4j
public class PetEvolutionServiceImpl implements PetEvolutionService {

    private static final int ATTRIBUTE_MAX = 999;

    private final PetService petService;
    private final PetEvolutionConfigMapper evolutionConfigMapper;
    private final PetMapper petMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetActivityMapper activityMapper;
    private final WishFeignClient wishFeignClient;
    private final PetAchievementService achievementService;
    private final PetEventProducer eventProducer;

    public PetEvolutionServiceImpl(PetService petService,
                                   PetEvolutionConfigMapper evolutionConfigMapper,
                                   PetMapper petMapper,
                                   PetInventoryMapper inventoryMapper,
                                   PetActivityMapper activityMapper,
                                   WishFeignClient wishFeignClient,
                                   PetAchievementService achievementService,
                                   PetEventProducer eventProducer) {
        this.petService = petService;
        this.evolutionConfigMapper = evolutionConfigMapper;
        this.petMapper = petMapper;
        this.inventoryMapper = inventoryMapper;
        this.activityMapper = activityMapper;
        this.wishFeignClient = wishFeignClient;
        this.achievementService = achievementService;
        this.eventProducer = eventProducer;
    }

    @Override
    public PetEvolutionVO status(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        return buildStatus(pet, starlightBalanceQuietly(userId));
    }

    @Override
    @Transactional
    public PetEvolutionVO evolve(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        int stage = currentStage(pet);
        List<PetEvolutionConfig> configs = enabledConfigs();
        PetEvolutionConfig next = nextConfig(configs, stage);
        if (next == null) {
            throw new BusinessException(PetErrorCodes.PET_EVOLUTION_MAX, "宠物已经进化到最高阶段啦");
        }
        int requiredLevel = next.getRequiredLevel() != null ? next.getRequiredLevel() : 1;
        if (pet.getLevel() < requiredLevel) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED,
                    "等级达到 Lv." + requiredLevel + " 才能进化哦");
        }

        // 1. 应用进化（属性一次性提升 + 阶段推进 + 可选皮肤解锁）
        pet.setEvolutionStage(next.getStageTo());
        pet.setMaxHp(pet.getMaxHp() + orZero(next.getBonusMaxHp()));
        pet.setHp(Math.min(pet.getMaxHp(), pet.getHp() + orZero(next.getBonusMaxHp())));
        pet.setStrength(grow(pet.getStrength(), next.getBonusStrength()));
        pet.setIntelligence(grow(pet.getIntelligence(), next.getBonusIntelligence()));
        pet.setAgility(grow(pet.getAgility(), next.getBonusAgility()));
        pet.setCharm(grow(pet.getCharm(), next.getBonusCharm()));
        petMapper.updateById(pet);
        grantUnlockSkin(pet, next.getUnlockSkinCode());
        recordEvolutionActivity(pet);

        // 2. 扣星光（失败整体回滚）
        int cost = orZero(next.getCostStarlight());
        if (cost > 0) {
            wishFeignClient.spendStarlight(userId, cost, pet.getId());
        }

        achievementService.evaluate(pet, PetAchievementService.Event.EVOLUTION);
        eventProducer.publish(RocketMQConfig.PET_TAG_EVOLVED, new PetEventProducer.PetEventMessage(
                userId, "PET_EVOLVED",
                "宠物进化啦！",
                pet.getName() + " 完成了「" + next.getName() + "」，快去看看它的新样子吧！",
                pet.getId(), "PET_EVOLVED"));
        return buildStatus(pet, starlightBalanceQuietly(userId));
    }

    private PetEvolutionVO buildStatus(Pet pet, Integer balance) {
        int stage = currentStage(pet);
        List<PetEvolutionConfig> configs = enabledConfigs();
        int maxStage = configs.stream()
                .mapToInt(config -> orZero(config.getStageTo()))
                .max().orElse(0);
        PetEvolutionConfig next = nextConfig(configs, stage);
        if (next == null) {
            return new PetEvolutionVO(stage, maxStage, null, null, null, null, null,
                    null, null, null, null, null, null, null, false, "已达到最高进化阶段");
        }
        int requiredLevel = next.getRequiredLevel() != null ? next.getRequiredLevel() : 1;
        int cost = orZero(next.getCostStarlight());
        boolean levelOk = pet.getLevel() >= requiredLevel;
        boolean balanceOk = balance == null || balance >= cost;
        boolean canEvolve = levelOk && balanceOk;
        String lockReason = null;
        if (!levelOk) {
            lockReason = "需要 Lv." + requiredLevel;
        } else if (!balanceOk) {
            lockReason = "星光不足（需要 " + cost + "）";
        }
        return new PetEvolutionVO(stage, maxStage, next.getCode(), next.getName(), next.getDescription(),
                next.getRequiredLevel(), cost, next.getBonusMaxHp(), next.getBonusStrength(),
                next.getBonusIntelligence(), next.getBonusAgility(), next.getBonusCharm(),
                next.getUnlockSkinCode(), next.getIcon(), canEvolve, lockReason);
    }

    private List<PetEvolutionConfig> enabledConfigs() {
        return evolutionConfigMapper.selectList(new LambdaQueryWrapper<PetEvolutionConfig>()
                .eq(PetEvolutionConfig::getEnabled, true)
                .orderByAsc(PetEvolutionConfig::getSort));
    }

    private PetEvolutionConfig nextConfig(List<PetEvolutionConfig> configs, int stage) {
        return configs.stream()
                .filter(config -> orZero(config.getStageFrom()) == stage)
                .findFirst()
                .orElse(null);
    }

    private int currentStage(Pet pet) {
        return pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0;
    }

    /** 进化解锁皮肤：直接入包（已拥有则跳过，不报错） */
    private void grantUnlockSkin(Pet pet, String skinCode) {
        if (skinCode == null || skinCode.isBlank()) {
            return;
        }
        PetInventory skin = new PetInventory();
        skin.setPetId(pet.getId());
        skin.setUserId(pet.getUserId());
        skin.setItemType(PetItemType.SKIN.name());
        skin.setItemCode(skinCode);
        skin.setQuantity(1);
        skin.setEquipped(false);
        skin.setAcquiredAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            inventoryMapper.insert(skin);
        } catch (DuplicateKeyException e) {
            log.debug("进化解锁皮肤已拥有，跳过入包: petId={}, skin={}", pet.getId(), skinCode);
        }
    }

    private void recordEvolutionActivity(Pet pet) {
        PetActivity activity = new PetActivity();
        activity.setPetId(pet.getId());
        activity.setUserId(pet.getUserId());
        activity.setActivityType(PetActivityType.EVOLVE.name());
        activity.setStatus(PetActivityStatus.CLAIMED.name());
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        activity.setStartedAt(now);
        activity.setFinishedAt(now);
        activity.setClaimedAt(now);
        activity.setResult("{\"evolutionStage\":" + currentStage(pet) + "}");
        activityMapper.insert(activity);
    }

    private int grow(Integer value, Integer bonus) {
        return Math.min(ATTRIBUTE_MAX, orZero(value) + orZero(bonus));
    }

    private int orZero(Integer value) {
        return value != null ? value : 0;
    }

    /** 余额查询：展示型数据 Fail-Open（null=不参与"星光是否足够"判定） */
    private Integer starlightBalanceQuietly(Long userId) {
        try {
            return wishFeignClient.starlightBalance(userId).data();
        } catch (Exception e) {
            log.warn("星光余额查询降级（Fail-Open）: userId={}", userId, e);
            return null;
        }
    }
}
