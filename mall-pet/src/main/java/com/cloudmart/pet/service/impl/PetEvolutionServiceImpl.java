package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetEvolutionConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetEvolutionConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetEvolutionService;
import com.cloudmart.pet.service.PetOperationRecoverable;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetEvolutionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宠物进化实现。
 *
 * <p>顺序（B01）：校验（等级/当前阶段）→ 幂等扣款（operationId = EVOLVE:petId:目标阶段，
 * 阶段转换一次性，键天然唯一）→ 本地应用进化（属性/皮肤/活动留痕）。扣款结果未知抛
 * PET_SETTLEMENT_PENDING；扣款成功但崩溃由恢复任务按快照幂等补应用（以当前阶段判定），
 * 永久无法履约按原单退款。进化链取自配置表，禁止硬编码阶段数值。</p>
 */
@Service
@Slf4j
public class PetEvolutionServiceImpl implements PetEvolutionService, PetOperationRecoverable {

    private static final int ATTRIBUTE_MAX = 999;
    private static final String BIZ_TYPE = "EVOLVE";

    private final PetService petService;
    private final PetEvolutionConfigMapper evolutionConfigMapper;
    private final PetMapper petMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetActivityMapper activityMapper;
    private final WishFeignClient wishFeignClient;
    private final PetAchievementService achievementService;
    private final PetOperationService operationService;
    private final PetOutboxService outboxService;
    private final PetClock petClock;

    public PetEvolutionServiceImpl(PetService petService,
                                   PetEvolutionConfigMapper evolutionConfigMapper,
                                   PetMapper petMapper,
                                   PetInventoryMapper inventoryMapper,
                                   PetActivityMapper activityMapper,
                                   WishFeignClient wishFeignClient,
                                   PetAchievementService achievementService,
                                   PetOperationService operationService,
                                   PetOutboxService outboxService,
                                   PetClock petClock) {
        this.petService = petService;
        this.evolutionConfigMapper = evolutionConfigMapper;
        this.petMapper = petMapper;
        this.inventoryMapper = inventoryMapper;
        this.activityMapper = activityMapper;
        this.wishFeignClient = wishFeignClient;
        this.achievementService = achievementService;
        this.operationService = operationService;
        this.outboxService = outboxService;
        this.petClock = petClock;
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

        // 1. 幂等扣款（结果未知 → 结算中，按原请求重试幂等；禁止换单号二次扣款）
        int cost = orZero(next.getCostStarlight());
        if (cost > 0) {
            String operationId = operationService.operationKey(BIZ_TYPE, userId, pet.getId(), next.getStageTo());
            PetOperationService.WalletSettlement settlement = operationService.executeSpend(
                    operationId, userId, pet.getId(), BIZ_TYPE, pet.getId(), cost, snapshot(pet, next, cost));
            if (settlement.isUnknown()) {
                throw operationService.settlementPending();
            }
            if (!settlement.isCompleted()) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "星光扣款未完成: " + settlement.lastError());
            }
        }

        // 2. 应用进化（属性一次性提升 + 阶段推进 + 可选皮肤解锁）
        applyEvolution(pet, next);
        recordEvolutionActivity(pet);

        achievementService.evaluate(pet, PetAchievementService.Event.EVOLUTION);
        String eventId = "EVOLVED:" + pet.getId() + ":" + next.getStageTo();
        outboxService.record(eventId, RocketMQConfig.PET_TAG_EVOLVED, userId, pet.getId(),
                new com.cloudmart.pet.mq.PetEventProducer.PetEventMessage(
                        eventId, String.valueOf(userId), "PET_EVOLVED",
                        "宠物进化啦！",
                        pet.getName() + " 完成了「" + next.getName() + "」，快去看看它的新样子吧！",
                        String.valueOf(pet.getId()), "PET_EVOLVED"));
        return buildStatus(pet, starlightBalanceQuietly(userId));
    }

    /** 进化本地效果：以当前阶段幂等（重复应用时阶段已达标直接跳过，不叠加属性） */
    private void applyEvolution(Pet pet, PetEvolutionConfig next) {
        if (currentStage(pet) >= orZero(next.getStageTo())) {
            return;
        }
        pet.setEvolutionStage(next.getStageTo());
        pet.setMaxHp(pet.getMaxHp() + orZero(next.getBonusMaxHp()));
        pet.setHp(Math.min(pet.getMaxHp(), pet.getHp() + orZero(next.getBonusMaxHp())));
        pet.setStrength(grow(pet.getStrength(), next.getBonusStrength()));
        pet.setIntelligence(grow(pet.getIntelligence(), next.getBonusIntelligence()));
        pet.setAgility(grow(pet.getAgility(), next.getBonusAgility()));
        pet.setCharm(grow(pet.getCharm(), next.getBonusCharm()));
        int updated = petMapper.updateById(pet);
        if (updated == 0) {
            // B02：版本冲突必须显式失败，禁止静默丢更新
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                    "宠物状态被并发修改，请稍后重试");
        }
        grantUnlockSkin(pet, next.getUnlockSkinCode());
    }

    private String snapshot(Pet pet, PetEvolutionConfig next, int cost) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("stageTo", next.getStageTo());
        snapshot.put("bonusMaxHp", orZero(next.getBonusMaxHp()));
        snapshot.put("bonusStrength", orZero(next.getBonusStrength()));
        snapshot.put("bonusIntelligence", orZero(next.getBonusIntelligence()));
        snapshot.put("bonusAgility", orZero(next.getBonusAgility()));
        snapshot.put("bonusCharm", orZero(next.getBonusCharm()));
        snapshot.put("unlockSkinCode", next.getUnlockSkinCode());
        snapshot.put("cost", cost);
        return PetJsonUtils.toJson(snapshot);
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
        skin.setAcquiredAt(petClock.nowUtc());
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
        var now = petClock.nowUtc();
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

    @Override
    public String supportedBizType() {
        return BIZ_TYPE;
    }

    /** 恢复任务回调（B01）：钱包已扣款但本地进化未应用时，按快照幂等补应用（阶段已达标=已履约） */
    @Override
    public boolean completePendingOperation(PetOperation operation) {
        Map<String, Object> snapshot = PetJsonUtils.parse(operation.getRewardSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        int stageTo = ((Number) snapshot.get("stageTo")).intValue();
        Pet pet = petMapper.selectById(operation.getPetId());
        if (pet == null) {
            return false;
        }
        if (currentStage(pet) >= stageTo) {
            return true;
        }
        pet.setEvolutionStage(stageTo);
        pet.setMaxHp(pet.getMaxHp() + intOf(snapshot.get("bonusMaxHp")));
        pet.setHp(Math.min(pet.getMaxHp(), pet.getHp() + intOf(snapshot.get("bonusMaxHp"))));
        pet.setStrength(grow(pet.getStrength(), intOf(snapshot.get("bonusStrength"))));
        pet.setIntelligence(grow(pet.getIntelligence(), intOf(snapshot.get("bonusIntelligence"))));
        pet.setAgility(grow(pet.getAgility(), intOf(snapshot.get("bonusAgility"))));
        pet.setCharm(grow(pet.getCharm(), intOf(snapshot.get("bonusCharm"))));
        petMapper.updateById(pet);
        grantUnlockSkin(pet, (String) snapshot.get("unlockSkinCode"));
        recordEvolutionActivity(pet);
        return true;
    }

    private int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
