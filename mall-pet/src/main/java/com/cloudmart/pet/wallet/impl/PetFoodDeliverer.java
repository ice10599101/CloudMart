package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.service.impl.PetCooperationService;
import com.cloudmart.pet.wallet.PetPurchaseCatalog.PetAssetDeliverer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 食物堆叠交付器（R02/F1）：食物购买按 (pet, FOOD, code) 原子堆叠 quantity+1，
 * 无行则插入（uk 并发兜底重试一次）。同一次意图重放不会二次进本交付器——
 * dedup 终态与订单同一事务，重放直接返回原结果。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PetFoodDeliverer implements PetAssetDeliverer {

    private final PetInventoryMapper inventoryMapper;
    private final PetCooperationService cooperationService;

    @Override
    public boolean supports(String itemType) {
        return "FOOD".equals(itemType);
    }

    @Override
    public List<String> deliver(DeliveryContext context) {
        int updated = inventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                .setSql("quantity = quantity + 1")
                .eq(PetInventory::getPetId, context.petId())
                .eq(PetInventory::getItemType, context.itemType())
                .eq(PetInventory::getItemCode, context.itemCode()));
        if (updated == 0) {
            PetInventory item = new PetInventory();
            item.setPetId(context.petId());
            item.setUserId(context.userId());
            item.setItemType(context.itemType());
            item.setItemCode(context.itemCode());
            item.setQuantity(1);
            item.setEquipped(false);
            item.setAcquiredAt(LocalDateTime.now(ZoneOffset.UTC));
            try {
                inventoryMapper.insert(item);
            } catch (DuplicateKeyException e) {
                // 并发首购：重试一次堆叠
                inventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                        .setSql("quantity = quantity + 1")
                        .eq(PetInventory::getPetId, context.petId())
                        .eq(PetInventory::getItemType, context.itemType())
                        .eq(PetInventory::getItemCode, context.itemCode()));
            }
        }
        cooperationService.unlockCollection(context.userId(), context.petId(),
                context.itemType(), context.itemCode(),
                "ORDER:" + context.orderId() + ":" + context.itemType() + ":" + context.itemCode());
        return List.of(context.itemCode());
    }
}
