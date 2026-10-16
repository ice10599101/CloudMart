package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
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
 *
 * <p>PET-01：堆叠条件的 pet_id 与 user_id 必须同时匹配（归属已在购买事务入口校验，
 * 这里是交付层的纵深防御）——同键行存在但归属不一致时明确失败回滚，不静默吞掉交付。</p>
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
        if (stackOne(context) == 0) {
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
                // 并发首购：重试一次堆叠；仍为 0 说明同键行归属不一致（数据异常），
                // 明确失败让整单回滚，不产生"订单成功但食物没进背包"的静默丢失
                if (stackOne(context) == 0) {
                    throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT,
                            "背包数据异常，请稍后重试");
                }
            }
        }
        cooperationService.unlockCollection(context.userId(), context.petId(),
                context.itemType(), context.itemCode(),
                "ORDER:" + context.orderId() + ":" + context.itemType() + ":" + context.itemCode());
        return List.of(context.itemCode());
    }

    /** 按 (pet_id, user_id, item_type, item_code) 堆叠 +1；返回受影响行数 */
    private int stackOne(DeliveryContext context) {
        return inventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                .setSql("quantity = quantity + 1")
                .eq(PetInventory::getPetId, context.petId())
                .eq(PetInventory::getUserId, context.userId())
                .eq(PetInventory::getItemType, context.itemType())
                .eq(PetInventory::getItemCode, context.itemCode()));
    }
}
