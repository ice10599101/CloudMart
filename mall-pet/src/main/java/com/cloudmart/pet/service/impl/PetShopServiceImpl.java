package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.service.PetOperationRecoverable;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetShopService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetShopItemVO;
import com.cloudmart.pet.vo.PetShopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宠物商城实现。
 *
 * <p>购买顺序（B01）：先可校验的配置/资格校验 → 幂等扣款（operationId =
 * SHOP_BUY:user:pet:类型:编码，钱包端按业务操作键去重）→ 本地入包（uk 唯一键幂等）。
 * 扣款结果未知（超时/降级）抛 PET_SETTLEMENT_PENDING，客户端按原请求重试幂等，
 * 禁止重新生成一笔独立交易；扣款成功但进程崩溃时由恢复任务按 rewardSnapshot
 * 幂等补入包，永久无法履约则按原单号派生唯一补偿单退款。</p>
 */
@Service
@Slf4j
public class PetShopServiceImpl implements PetShopService, PetOperationRecoverable {

    private static final String BIZ_TYPE = "SHOP_BUY";

    private final PetService petService;
    private final PetItemCatalog itemCatalog;
    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkinConfigMapper skinConfigMapper;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetSkillMapper skillMapper;
    private final WishFeignClient wishFeignClient;
    private final PetOperationService operationService;
    private final PetClock petClock;

    public PetShopServiceImpl(PetService petService,
                              PetItemCatalog itemCatalog,
                              PetEquipmentConfigMapper equipmentConfigMapper,
                              PetSkinConfigMapper skinConfigMapper,
                              PetSkillConfigMapper skillConfigMapper,
                              PetInventoryMapper inventoryMapper,
                              PetSkillMapper skillMapper,
                              WishFeignClient wishFeignClient,
                              PetOperationService operationService,
                              PetClock petClock) {
        this.petService = petService;
        this.itemCatalog = itemCatalog;
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skinConfigMapper = skinConfigMapper;
        this.skillConfigMapper = skillConfigMapper;
        this.inventoryMapper = inventoryMapper;
        this.skillMapper = skillMapper;
        this.wishFeignClient = wishFeignClient;
        this.operationService = operationService;
        this.petClock = petClock;
    }

    @Override
    public PetShopVO shop(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        Map<PetItemType, Set<String>> owned = ownedCodesByType(pet.getId());
        Set<String> learnedSkills = learnedSkillCodes(pet.getId());

        List<PetShopItemVO> items = new ArrayList<>();
        equipmentConfigMapper.selectList(new LambdaQueryWrapper<PetEquipmentConfig>()
                        .eq(PetEquipmentConfig::getEnabled, true)
                        .orderByAsc(PetEquipmentConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        owned.getOrDefault(PetItemType.EQUIPMENT, Set.of()).contains(config.getCode()),
                        equipmentLockReason(pet, config))));
        skinConfigMapper.selectList(new LambdaQueryWrapper<PetSkinConfig>()
                        .eq(PetSkinConfig::getEnabled, true)
                        .orderByAsc(PetSkinConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        owned.getOrDefault(PetItemType.SKIN, Set.of()).contains(config.getCode()),
                        skinLockReason(pet, config))));
        skillConfigMapper.selectList(new LambdaQueryWrapper<PetSkillConfig>()
                        .eq(PetSkillConfig::getEnabled, true)
                        .orderByAsc(PetSkillConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        learnedSkills.contains(config.getCode())
                                || owned.getOrDefault(PetItemType.SKILL_BOOK, Set.of()).contains(config.getCode()),
                        skillLockReason(pet, config))));
        return new PetShopVO(starlightBalanceQuietly(userId), items);
    }

    @Override
    @Transactional
    public PetInventoryItemVO buy(Long userId, BuyItemRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        return switch (PetItemType.valueOf(request.itemType())) {
            case EQUIPMENT -> buyEquipment(pet, request.itemCode());
            case SKIN -> buySkin(pet, request.itemCode());
            case SKILL_BOOK -> buySkillBook(pet, request.itemCode());
            // 三期家具走家园商城（/home/furniture/buy）：这里显式拒绝，避免前端走错入口默默失败
            case FURNITURE -> throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "家具请到家园商城购买哦");
        };
    }

    private PetInventoryItemVO buyEquipment(Pet pet, String code) {
        PetEquipmentConfig config = itemCatalog.equipment(code)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_ITEM_NOT_FOUND,
                        "这件装备不存在或已下架"));
        if (owned(pet.getId(), PetItemType.EQUIPMENT, code)) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "背包里已经有这件装备啦");
        }
        requireEligible(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());
        spendForPurchase(pet, PetItemType.EQUIPMENT, code, price(config.getPriceStarlight()));
        PetInventory item = insertInventory(pet, PetItemType.EQUIPMENT, code, config.getSlot());
        return itemCatalog.toInventoryVo(item, false);
    }

    private PetInventoryItemVO buySkin(Pet pet, String code) {
        PetSkinConfig config = itemCatalog.skin(code)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_ITEM_NOT_FOUND,
                        "这套皮肤不存在或已下架"));
        if (owned(pet.getId(), PetItemType.SKIN, code)) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "衣柜里已经有这套皮肤啦");
        }
        requireEligible(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());
        requireSpeciesMatch(pet, config);
        spendForPurchase(pet, PetItemType.SKIN, code, price(config.getPriceStarlight()));
        PetInventory item = insertInventory(pet, PetItemType.SKIN, code, null);
        return itemCatalog.toInventoryVo(item, false);
    }

    private PetInventoryItemVO buySkillBook(Pet pet, String code) {
        PetSkillConfig config = itemCatalog.skill(code)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_SKILL_NOT_FOUND,
                        "这个技能不存在或已下架"));
        // 技能书"已购买未学习"也算拥有（B12）：拥有判断看背包，而不是已学技能
        if (owned(pet.getId(), PetItemType.SKILL_BOOK, code)) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "这本技能书已经在背包里啦");
        }
        requireEligible(pet, config.getRequiredLevel(), 0);
        spendForPurchase(pet, PetItemType.SKILL_BOOK, code, price(config.getPriceStarlight()));
        PetInventory item = insertInventory(pet, PetItemType.SKILL_BOOK, code, null);
        return itemCatalog.toInventoryVo(item, false);
    }

    private int price(Integer configPrice) {
        return configPrice != null ? configPrice : 0;
    }

    /** 幂等扣款：价格 0 无资金流动，跳过交易（入包靠唯一键幂等） */
    private void spendForPurchase(Pet pet, PetItemType itemType, String code, int cost) {
        if (cost <= 0) {
            return;
        }
        String operationId = operationService.operationKey(BIZ_TYPE,
                pet.getUserId(), pet.getId(), itemType.name(), code);
        String snapshot = PetJsonUtils.toJson(Map.of(
                "itemType", itemType.name(),
                "itemCode", code,
                "price", cost));
        PetOperationService.WalletSettlement settlement = operationService.executeSpend(
                operationId, pet.getUserId(), pet.getId(), BIZ_TYPE, null, cost, snapshot);
        if (settlement.isUnknown()) {
            throw operationService.settlementPending();
        }
        if (!settlement.isCompleted()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "星光扣款未完成: " + settlement.lastError());
        }
    }

    private PetInventory insertInventory(Pet pet, PetItemType type, String code, String slot) {
        PetInventory item = new PetInventory();
        item.setPetId(pet.getId());
        item.setUserId(pet.getUserId());
        item.setItemType(type.name());
        item.setItemCode(code);
        item.setQuantity(1);
        item.setEquipped(false);
        item.setSlot(slot);
        item.setAcquiredAt(petClock.nowUtc());
        try {
            inventoryMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 并发重复购买：uk_pet_inventory_item 兜底
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "已经拥有这个物品啦");
        }
        return item;
    }

    private void requireEligible(Pet pet, Integer requiredLevel, Integer requiredEvolutionStage) {
        int level = requiredLevel != null ? requiredLevel : 1;
        if (pet.getLevel() < level) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED, "等级达到 Lv." + level + " 才能购买哦");
        }
        int stage = requiredEvolutionStage != null ? requiredEvolutionStage : 0;
        int currentStage = pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0;
        if (currentStage < stage) {
            throw new BusinessException(PetErrorCodes.PET_EVOLUTION_REQUIRED, "需要先完成进化才能购买");
        }
    }

    private void requireSpeciesMatch(Pet pet, PetSkinConfig config) {
        if (config.getSpecies() != null && !config.getSpecies().isBlank()
                && !config.getSpecies().equals(pet.getSpecies())) {
            throw new BusinessException(PetErrorCodes.PET_SKIN_SPECIES_MISMATCH,
                    "这套皮肤只适合 " + config.getSpecies() + " 种类");
        }
    }

    private String equipmentLockReason(Pet pet, PetEquipmentConfig config) {
        int level = config.getRequiredLevel() != null ? config.getRequiredLevel() : 1;
        if (pet.getLevel() < level) {
            return "需要 Lv." + level;
        }
        int stage = config.getRequiredEvolutionStage() != null ? config.getRequiredEvolutionStage() : 0;
        if ((pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) < stage) {
            return "需要进化 " + stage + " 阶";
        }
        return null;
    }

    private String skinLockReason(Pet pet, PetSkinConfig config) {
        if (config.getSpecies() != null && !config.getSpecies().isBlank()
                && !config.getSpecies().equals(pet.getSpecies())) {
            return "限定 " + config.getSpecies() + " 种类";
        }
        return equipmentStageReason(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());
    }

    private String skillLockReason(Pet pet, PetSkillConfig config) {
        return equipmentStageReason(pet, config.getRequiredLevel(), 0);
    }

    private String equipmentStageReason(Pet pet, Integer requiredLevel, Integer requiredEvolutionStage) {
        int level = requiredLevel != null ? requiredLevel : 1;
        if (pet.getLevel() < level) {
            return "需要 Lv." + level;
        }
        int stage = requiredEvolutionStage != null ? requiredEvolutionStage : 0;
        if ((pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) < stage) {
            return "需要进化 " + stage + " 阶";
        }
        return null;
    }

    private boolean owned(Long petId, PetItemType type, String code) {
        return inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, petId)
                .eq(PetInventory::getItemType, type.name())
                .eq(PetInventory::getItemCode, code)) > 0;
    }

    /** 按 (petId, itemType, itemCode) 分型聚合拥有集合（B12：装备/皮肤/技能书编码空间独立） */
    private Map<PetItemType, Set<String>> ownedCodesByType(Long petId) {
        Map<PetItemType, Set<String>> owned = new HashMap<>();
        inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, petId))
                .forEach(item -> owned
                        .computeIfAbsent(PetItemType.valueOf(item.getItemType()), type -> new HashSet<>())
                        .add(item.getItemCode()));
        return owned;
    }

    private Set<String> learnedSkillCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        skillMapper.selectList(new LambdaQueryWrapper<PetSkill>()
                        .eq(PetSkill::getPetId, petId))
                .forEach(skill -> codes.add(skill.getSkillCode()));
        return codes;
    }

    /** 余额查询：展示型数据 Fail-Open（null=前端隐藏余额，不阻断商城浏览） */
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

    /**
     * 恢复任务回调（B01）：钱包已扣款但本地入包未落地时，按 rewardSnapshot 幂等补入包。
     * 已拥有（DuplicateKey/存在查询）视为已履约。
     */
    @Override
    public boolean completePendingOperation(PetOperation operation) {
        Map<String, Object> snapshot = PetJsonUtils.parse(operation.getRewardSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        String itemType = String.valueOf(snapshot.get("itemType"));
        String itemCode = String.valueOf(snapshot.get("itemCode"));
        boolean exists = inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, operation.getPetId())
                .eq(PetInventory::getItemType, itemType)
                .eq(PetInventory::getItemCode, itemCode)) > 0;
        if (exists) {
            return true;
        }
        PetInventory item = new PetInventory();
        item.setPetId(operation.getPetId());
        item.setUserId(operation.getUserId());
        item.setItemType(itemType);
        item.setItemCode(itemCode);
        item.setQuantity(1);
        item.setEquipped(false);
        item.setAcquiredAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        try {
            inventoryMapper.insert(item);
            return true;
        } catch (DuplicateKeyException e) {
            return true;
        }
    }
}
