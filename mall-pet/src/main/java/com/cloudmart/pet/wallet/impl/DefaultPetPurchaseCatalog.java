package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetFoodConfig;
import com.cloudmart.pet.entity.PetFurnitureConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetFoodConfigMapper;
import com.cloudmart.pet.repository.PetFurnitureConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * PetPurchaseCatalog 默认实现（W01 SPI 桥接 + R02 收口）：读宠物五类配置表
 * （装备/皮肤/技能书/家具/食物）做服务端权威价格快照、上下架/等级/进化/种类门槛校验
 * 与按宠物唯一性判定。
 *
 * <p>R02 修正（相对 W01 版本）：</p>
 * <ul>
 *   <li>补 FOOD 目录（原实现不支持，食物购买走不到统一购买链）；</li>
 *   <li>补上架/等级/进化/种类门槛校验（原实现只查价格，下架商品仍可扣款成交）；</li>
 *   <li>唯一性改为 petId+类型+编码（原实现按 userId 全账号判定，与背包
 *       uk(pet_id,item_type,item_code) 语义不符——多宠各自拥有各自的东西）。</li>
 * </ul>
 *
 * <p>快照 configVersion 取配置行 updatedAt（毫秒）——配置改动即版本变化，
 * expectedConfigVersion 不匹配按契约抛 PET_CONFIG_VERSION_CONFLICT。</p>
 */
@Component
@Slf4j
public class DefaultPetPurchaseCatalog implements PetPurchaseCatalog {

    private static final DateTimeFormatter VERSION_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkinConfigMapper skinConfigMapper;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetFurnitureConfigMapper furnitureConfigMapper;
    private final PetFoodConfigMapper foodConfigMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetMapper petMapper;

    public DefaultPetPurchaseCatalog(PetEquipmentConfigMapper equipmentConfigMapper,
                                     PetSkinConfigMapper skinConfigMapper,
                                     PetSkillConfigMapper skillConfigMapper,
                                     PetFurnitureConfigMapper furnitureConfigMapper,
                                     PetFoodConfigMapper foodConfigMapper,
                                     PetInventoryMapper inventoryMapper,
                                     PetMapper petMapper) {
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skinConfigMapper = skinConfigMapper;
        this.skillConfigMapper = skillConfigMapper;
        this.furnitureConfigMapper = furnitureConfigMapper;
        this.foodConfigMapper = foodConfigMapper;
        this.inventoryMapper = inventoryMapper;
        this.petMapper = petMapper;
    }

    @Override
    public CatalogEntry load(Long userId, Long petId, String itemType, String itemCode,
                             String expectedConfigVersion) {
        CatalogEntry entry = switch (itemType == null ? "" : itemType) {
            case "EQUIPMENT" -> loadEquipment(itemCode);
            case "SKIN" -> loadSkin(itemCode);
            case "SKILL_BOOK" -> loadSkillBook(itemCode);
            case "FURNITURE" -> loadFurniture(itemCode);
            case "FOOD" -> loadFood(itemCode);
            default -> throw new BusinessException("PET_ITEM_TYPE_INVALID", "非法商品类型: " + itemType);
        };
        if (entry == null) {
            throw new BusinessException("PET_ITEM_NOT_FOUND", "商品不存在: " + itemCode);
        }
        requireEligible(petId, itemType, entry);
        if (expectedConfigVersion != null && !expectedConfigVersion.isBlank()
                && !expectedConfigVersion.equals(entry.configVersion())) {
            throw new BusinessException("PET_CONFIG_VERSION_CONFLICT",
                    "商品配置已变更，请刷新后重试");
        }
        if (isUniquePerPet(itemType) && isOwnedByPet(petId, itemType, itemCode)) {
            throw new AlreadyOwnedException("已拥有该物品: " + itemCode);
        }
        return entry;
    }

    private CatalogEntry loadEquipment(String code) {
        PetEquipmentConfig c = equipmentConfigMapper.selectOne(
                new LambdaQueryWrapper<PetEquipmentConfig>().eq(PetEquipmentConfig::getCode, code));
        if (c == null || !Boolean.TRUE.equals(c.getEnabled())) {
            return null;
        }
        return new CatalogEntry(PetItemType.EQUIPMENT.name(), c.getCode(),
                price(c.getPriceStarlight()), versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    private CatalogEntry loadSkin(String code) {
        PetSkinConfig c = skinConfigMapper.selectOne(
                new LambdaQueryWrapper<PetSkinConfig>().eq(PetSkinConfig::getCode, code));
        if (c == null || !Boolean.TRUE.equals(c.getEnabled())) {
            return null;
        }
        return new CatalogEntry(PetItemType.SKIN.name(), c.getCode(),
                price(c.getPriceStarlight()), versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    private CatalogEntry loadSkillBook(String code) {
        PetSkillConfig c = skillConfigMapper.selectOne(
                new LambdaQueryWrapper<PetSkillConfig>().eq(PetSkillConfig::getCode, code));
        if (c == null || !Boolean.TRUE.equals(c.getEnabled())) {
            return null;
        }
        return new CatalogEntry(PetItemType.SKILL_BOOK.name(), c.getCode(),
                price(c.getPriceStarlight()), versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    private CatalogEntry loadFurniture(String code) {
        PetFurnitureConfig c = furnitureConfigMapper.selectOne(
                new LambdaQueryWrapper<PetFurnitureConfig>().eq(PetFurnitureConfig::getCode, code));
        if (c == null || !Boolean.TRUE.equals(c.getEnabled())) {
            return null;
        }
        return new CatalogEntry(PetItemType.FURNITURE.name(), c.getCode(),
                price(c.getPriceStarlight()), versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    private CatalogEntry loadFood(String code) {
        PetFoodConfig c = foodConfigMapper.selectOne(
                new LambdaQueryWrapper<PetFoodConfig>().eq(PetFoodConfig::getCode, code));
        if (c == null || !Integer.valueOf(1).equals(c.getEnabled())) {
            return null;
        }
        return new CatalogEntry(PetItemType.FOOD.name(), c.getCode(),
                price(c.getPriceStarlight()), versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    /**
     * 购买门槛（服务端权威，与商城展示 lockReason 同口径）：
     * 装备/皮肤/技能书按 requiredLevel（+装备/皮肤的进化阶段），皮肤另校验限定种类，家具按 requiredLevel。
     * 食物无门槛（喂养基础行为）。
     */
    private void requireEligible(Long petId, String itemType, CatalogEntry entry) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "目标宠物不存在");
        }
        switch (itemType) {
            case "EQUIPMENT" -> {
                PetEquipmentConfig c = equipmentConfigMapper.selectOne(
                        new LambdaQueryWrapper<PetEquipmentConfig>().eq(PetEquipmentConfig::getCode, entry.itemCode()));
                requireLevel(pet, c == null ? null : c.getRequiredLevel());
                requireEvolution(pet, c == null ? null : c.getRequiredEvolutionStage());
            }
            case "SKIN" -> {
                PetSkinConfig c = skinConfigMapper.selectOne(
                        new LambdaQueryWrapper<PetSkinConfig>().eq(PetSkinConfig::getCode, entry.itemCode()));
                requireLevel(pet, c == null ? null : c.getRequiredLevel());
                requireEvolution(pet, c == null ? null : c.getRequiredEvolutionStage());
                if (c != null && c.getSpecies() != null && !c.getSpecies().isBlank()
                        && !c.getSpecies().equals(pet.getSpecies())) {
                    throw new BusinessException(PetErrorCodes.PET_SKIN_SPECIES_MISMATCH,
                            "这套皮肤只适合 " + c.getSpecies() + " 种类");
                }
            }
            case "SKILL_BOOK" -> {
                PetSkillConfig c = skillConfigMapper.selectOne(
                        new LambdaQueryWrapper<PetSkillConfig>().eq(PetSkillConfig::getCode, entry.itemCode()));
                requireLevel(pet, c == null ? null : c.getRequiredLevel());
            }
            case "FURNITURE" -> {
                PetFurnitureConfig c = furnitureConfigMapper.selectOne(
                        new LambdaQueryWrapper<PetFurnitureConfig>().eq(PetFurnitureConfig::getCode, entry.itemCode()));
                requireLevel(pet, c == null ? null : c.getRequiredLevel());
            }
            default -> {
                // FOOD：无门槛
            }
        }
    }

    private void requireLevel(Pet pet, Integer requiredLevel) {
        int level = requiredLevel != null ? requiredLevel : 1;
        if (pet.getLevel() < level) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED,
                    "等级达到 Lv." + level + " 才能购买哦");
        }
    }

    private void requireEvolution(Pet pet, Integer requiredStage) {
        int stage = requiredStage != null ? requiredStage : 0;
        int currentStage = pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0;
        if (currentStage < stage) {
            throw new BusinessException(PetErrorCodes.PET_EVOLUTION_REQUIRED, "需要先完成进化才能购买");
        }
    }

    @Override
    public boolean isUniquePerPet(String itemType) {
        // 装备/皮肤/技能书/家具同一宠物仅一份；食物可重复购买按 (pet,code) 堆叠 quantity
        return !"FOOD".equals(itemType);
    }

    @Override
    public boolean isOwnedByPet(Long petId, String itemType, String itemCode) {
        Long count = inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, petId)
                .eq(PetInventory::getItemType, itemType)
                .eq(PetInventory::getItemCode, itemCode));
        return count != null && count > 0;
    }

    private int price(Integer configPrice) {
        return configPrice != null ? configPrice : 0;
    }

    private String versionOf(java.time.LocalDateTime updatedAt) {
        return updatedAt == null ? "0" : updatedAt.format(VERSION_FMT);
    }
}
