package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetFurnitureConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetFurnitureConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * PetPurchaseCatalog 默认实现（W01 SPI 桥接）：直接读宠物四类配置表
 * （装备/皮肤/技能书/家具）做服务端权威价格快照与拥有判定，
 * 供 PetPurchaseApplicationService 在商城目录接入（W02）前可运行。
 *
 * <p>快照 configVersion 取配置行 updatedAt（毫秒）——配置改动即版本变化，
 * expectedConfigVersion 不匹配按契约抛 PET_CONFIG_VERSION_CONFLICT。
 * W02 商城目录实现落地后本类可由 @ConditionalOnMissingBean 让位。</p>
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
    private final PetInventoryMapper inventoryMapper;

    public DefaultPetPurchaseCatalog(PetEquipmentConfigMapper equipmentConfigMapper,
                                     PetSkinConfigMapper skinConfigMapper,
                                     PetSkillConfigMapper skillConfigMapper,
                                     PetFurnitureConfigMapper furnitureConfigMapper,
                                     PetInventoryMapper inventoryMapper) {
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skinConfigMapper = skinConfigMapper;
        this.skillConfigMapper = skillConfigMapper;
        this.furnitureConfigMapper = furnitureConfigMapper;
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    public CatalogEntry load(Long userId, Long petId, String itemType, String itemCode,
                             String expectedConfigVersion) {
        CatalogEntry entry = switch (itemType == null ? "" : itemType) {
            case "EQUIPMENT" -> loadEquipment(itemCode);
            case "SKIN" -> loadSkin(itemCode);
            case "SKILL_BOOK" -> loadSkillBook(itemCode);
            case "FURNITURE" -> loadFurniture(itemCode);
            default -> throw new BusinessException("PET_ITEM_TYPE_INVALID", "非法商品类型: " + itemType);
        };
        if (entry == null) {
            throw new BusinessException("PET_ITEM_NOT_FOUND", "商品不存在: " + itemCode);
        }
        if (expectedConfigVersion != null && !expectedConfigVersion.isBlank()
                && !expectedConfigVersion.equals(entry.configVersion())) {
            throw new BusinessException("PET_CONFIG_VERSION_CONFLICT",
                    "商品配置已变更，请刷新后重试");
        }
        if (isUniquePerUser(itemType) && isOwnedByUser(userId, itemType, itemCode)) {
            throw new AlreadyOwnedException("已拥有该物品: " + itemCode);
        }
        return entry;
    }

    private CatalogEntry loadEquipment(String code) {
        PetEquipmentConfig c = equipmentConfigMapper.selectOne(
                new LambdaQueryWrapper<PetEquipmentConfig>().eq(PetEquipmentConfig::getCode, code));
        return c == null ? null : new CatalogEntry(PetItemType.EQUIPMENT.name(), c.getCode(),
                c.getPriceStarlight() == null ? 0 : c.getPriceStarlight(),
                versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    private CatalogEntry loadSkin(String code) {
        PetSkinConfig c = skinConfigMapper.selectOne(
                new LambdaQueryWrapper<PetSkinConfig>().eq(PetSkinConfig::getCode, code));
        return c == null ? null : new CatalogEntry(PetItemType.SKIN.name(), c.getCode(),
                c.getPriceStarlight() == null ? 0 : c.getPriceStarlight(),
                versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    private CatalogEntry loadSkillBook(String code) {
        PetSkillConfig c = skillConfigMapper.selectOne(
                new LambdaQueryWrapper<PetSkillConfig>().eq(PetSkillConfig::getCode, code));
        return c == null ? null : new CatalogEntry(PetItemType.SKILL_BOOK.name(), c.getCode(),
                c.getPriceStarlight() == null ? 0 : c.getPriceStarlight(),
                versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    private CatalogEntry loadFurniture(String code) {
        PetFurnitureConfig c = furnitureConfigMapper.selectOne(
                new LambdaQueryWrapper<PetFurnitureConfig>().eq(PetFurnitureConfig::getCode, code));
        return c == null ? null : new CatalogEntry(PetItemType.FURNITURE.name(), c.getCode(),
                c.getPriceStarlight() == null ? 0 : c.getPriceStarlight(),
                versionOf(c.getUpdatedAt()), c.getName(), c.getCode());
    }

    @Override
    public boolean isUniquePerUser(String itemType) {
        // 装备/皮肤为唯一外观资产；技能书可重复购买学习；家具可多件
        return "EQUIPMENT".equals(itemType) || "SKIN".equals(itemType);
    }

    @Override
    public boolean isOwnedByUser(Long userId, String itemType, String itemCode) {
        Long count = inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getUserId, userId)
                .eq(PetInventory::getItemType, itemType)
                .eq(PetInventory::getItemCode, itemCode));
        return count != null && count > 0;
    }

    private String versionOf(java.time.LocalDateTime updatedAt) {
        return updatedAt == null ? "0" : updatedAt.format(VERSION_FMT);
    }
}
