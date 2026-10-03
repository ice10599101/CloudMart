package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetFurnitureConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetFurnitureConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.service.impl.PetPlayFeatureService;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import com.cloudmart.pet.wallet.PetPurchaseCatalog.CatalogEntry;
import com.cloudmart.pet.wallet.PetPurchaseCatalog.PetAssetDeliverer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 唯一类资产交付器（R02）：装备/皮肤/技能书/家具购买后写入宠物背包
 * （uk(pet_id,item_type,item_code) 一宠物一份；重复插入=重复交付，抛 AlreadyOwned 整单回滚）。
 *
 * <p>槽位：装备取配置 slot，家具取配置 category（摆放时使用），皮肤/技能书无槽位。
 * 图鉴投影随交付事实推进（uk 幂等，重复获得仅解锁一次）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PetInventoryDeliverer implements PetAssetDeliverer {

    private final PetInventoryMapper inventoryMapper;
    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetFurnitureConfigMapper furnitureConfigMapper;
    private final PetPlayFeatureService playFeatureService;

    @Override
    public boolean supports(String itemType) {
        return "EQUIPMENT".equals(itemType) || "SKIN".equals(itemType)
                || "SKILL_BOOK".equals(itemType) || "FURNITURE".equals(itemType);
    }

    @Override
    public List<String> deliver(DeliveryContext context) {
        PetInventory item = new PetInventory();
        item.setPetId(context.petId());
        item.setUserId(context.userId());
        item.setItemType(context.itemType());
        item.setItemCode(context.itemCode());
        item.setQuantity(1);
        item.setEquipped(false);
        item.setSlot(resolveSlot(context.itemType(), context.itemCode()));
        item.setAcquiredAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
            inventoryMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 背包唯一键兜底（目录拥有判定与本次插入之间的并发窗口）：重复交付视同已拥有，
            // 抛 AlreadyOwned 让购买事务整体回滚（扣款一并回滚，不出现扣款无交付）
            throw new PetPurchaseCatalog.AlreadyOwnedException(
                    context.itemType() + ":" + context.itemCode());
        }
        playFeatureService.unlockCollection(context.userId(), context.petId(),
                context.itemType(), context.itemCode(),
                "ORDER:" + context.orderId() + ":" + context.itemType() + ":" + context.itemCode());
        return List.of(context.itemCode());
    }

    private String resolveSlot(String itemType, String itemCode) {
        return switch (itemType) {
            case "EQUIPMENT" -> {
                PetEquipmentConfig c = equipmentConfigMapper.selectOne(
                        new LambdaQueryWrapper<PetEquipmentConfig>().eq(PetEquipmentConfig::getCode, itemCode));
                yield c == null ? null : c.getSlot();
            }
            case "FURNITURE" -> {
                PetFurnitureConfig c = furnitureConfigMapper.selectOne(
                        new LambdaQueryWrapper<PetFurnitureConfig>().eq(PetFurnitureConfig::getCode, itemCode));
                yield c == null ? null : c.getCategory();
            }
            default -> null;
        };
    }
}
