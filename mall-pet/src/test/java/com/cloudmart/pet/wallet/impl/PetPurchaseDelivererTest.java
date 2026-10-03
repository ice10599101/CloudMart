package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.service.impl.PetPlayFeatureService;
import com.cloudmart.pet.wallet.PetPurchaseCatalog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R02 交付器测试：食物按 (pet,FOOD,code) 原子堆叠；唯一类资产插入背包，
 * uk 冲突抛 AlreadyOwned 整单回滚（扣款一并回滚，不出现扣款无交付）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("购买交付器测试（R02）")
class PetPurchaseDelivererTest {

    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetPlayFeatureService playFeatureService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
    }

    @Test
    @DisplayName("食物堆叠：无行 insert quantity=1，图鉴投影推进")
    void foodDelivererInserts() {
        PetFoodDeliverer deliverer = new PetFoodDeliverer(inventoryMapper, playFeatureService);
        when(inventoryMapper.update(any(), any())).thenReturn(0);
        when(inventoryMapper.insert(any(PetInventory.class))).thenReturn(1);

        var slots = deliverer.deliver(context("FOOD", "apple"));

        assertThat(slots).containsExactly("apple");
        verify(inventoryMapper).insert(any(PetInventory.class));
        verify(playFeatureService).unlockCollection(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("食物堆叠：已有行条件更新 +1，不 insert")
    void foodDelivererStacksExistingRow() {
        PetFoodDeliverer deliverer = new PetFoodDeliverer(inventoryMapper, playFeatureService);
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        deliverer.deliver(context("FOOD", "apple"));

        verify(inventoryMapper, never()).insert(any(PetInventory.class));
    }

    @Test
    @DisplayName("食物堆叠：并发首购 uk 冲突转堆叠，不抛出")
    void foodDelivererDuplicateKeyFallsBackToStack() {
        PetFoodDeliverer deliverer = new PetFoodDeliverer(inventoryMapper, playFeatureService);
        when(inventoryMapper.update(any(), any())).thenReturn(0)
                .thenReturn(1);
        when(inventoryMapper.insert(any(PetInventory.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_pet_inventory_item"));

        assertThat(deliverer.deliver(context("FOOD", "apple"))).containsExactly("apple");
    }

    @Test
    @DisplayName("唯一类资产：插入背包返回槽位；重复交付抛 AlreadyOwned（整单回滚）")
    void inventoryDelivererInsertsAndRejectsDuplicate() {
        com.cloudmart.pet.repository.PetEquipmentConfigMapper equipmentConfigMapper =
                org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetEquipmentConfigMapper.class);
        com.cloudmart.pet.repository.PetFurnitureConfigMapper furnitureConfigMapper =
                org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetFurnitureConfigMapper.class);
        PetInventoryDeliverer deliverer = new PetInventoryDeliverer(inventoryMapper,
                equipmentConfigMapper, furnitureConfigMapper, playFeatureService);
        when(inventoryMapper.insert(any(PetInventory.class))).thenReturn(1);

        assertThat(deliverer.deliver(context("EQUIPMENT", "straw_hat"))).containsExactly("straw_hat");

        when(inventoryMapper.insert(any(PetInventory.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_pet_inventory_item"));
        assertThatThrownBy(() -> deliverer.deliver(context("EQUIPMENT", "straw_hat")))
                .isInstanceOf(PetPurchaseCatalog.AlreadyOwnedException.class);
    }

    private PetPurchaseCatalog.PetAssetDeliverer.DeliveryContext context(String itemType, String itemCode) {
        return new PetPurchaseCatalog.PetAssetDeliverer.DeliveryContext(
                100L, 1L, 900L, itemType, itemCode, 1,
                new PetPurchaseCatalog.CatalogEntry(itemType, itemCode, 10, "v1", itemCode, itemCode));
    }
}
