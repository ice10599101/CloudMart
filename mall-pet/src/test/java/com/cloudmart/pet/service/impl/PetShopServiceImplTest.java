package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetShopItemVO;
import com.cloudmart.pet.vo.PetShopVO;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C13：购买统一到 /pet/purchases 后，PetShopServiceImpl 只剩目录装配——
 * 装备/皮肤/技能书（拥有/已学与门槛锁定原因）+ 食物 + Fail-Open 余额。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetShopServiceImpl 目录装配测试（C13）")
class PetShopServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetItemCatalog itemCatalog;
    @Mock
    private PetEquipmentConfigMapper equipmentConfigMapper;
    @Mock
    private PetSkinConfigMapper skinConfigMapper;
    @Mock
    private PetSkillConfigMapper skillConfigMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetSkillMapper skillMapper;
    @Mock
    private PetEconomyService economyService;

    private PetShopServiceImpl shopService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
        TableInfoHelper.initTableInfo(assistant, PetSkill.class);
    }

    @BeforeEach
    void setUp() {
        shopService = new PetShopServiceImpl(petService, itemCatalog, equipmentConfigMapper, skinConfigMapper,
                skillConfigMapper, inventoryMapper, skillMapper, economyService);
        lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
        lenient().when(skinConfigMapper.selectList(any())).thenReturn(List.of());
        lenient().when(skillConfigMapper.selectList(any())).thenReturn(List.of());
        lenient().when(skillMapper.selectList(any())).thenReturn(List.of());
        lenient().when(itemCatalog.listFoods()).thenReturn(List.of(
                new PetItemCatalog.FoodItem("apple", "苹果", "🍎", "喂食 +10", 10, 10, 5, 2)));
        lenient().when(itemCatalog.toShopItem(any(PetEquipmentConfig.class),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(shopItem("EQUIPMENT", "hat"));
    }

    @Test
    @DisplayName("目录装配：已拥有装备 owned=true 且无锁；食物在列；币种 PET_COIN 与余额透出")
    void shop_assemblesCatalogWithOwnedStateAndBalance() {
        PetEquipmentConfig hat = equipment("hat", 1);
        when(equipmentConfigMapper.selectList(any())).thenReturn(List.of(hat));
        when(inventoryMapper.selectList(any())).thenReturn(List.of(inventory("EQUIPMENT", "hat")));
        when(economyService.mode()).thenReturn(PetEconomyService.Mode.PET);
        when(economyService.balanceOf(100L)).thenReturn(500L);

        PetShopVO vo = shopService.shop(100L);

        assertThat(vo.currency()).isEqualTo("PET_COIN");
        assertThat(vo.balance()).isEqualTo(500L);
        assertThat(vo.items()).extracting(PetShopItemVO::code).containsExactly("hat", "apple");
        verify(itemCatalog).toShopItem(hat, true, null);
    }

    @Test
    @DisplayName("等级门槛未达：装备 owned=false 且带锁定原因")
    void shop_marksEquipmentLockedByLevel() {
        PetEquipmentConfig hat = equipment("hat", 8);
        when(equipmentConfigMapper.selectList(any())).thenReturn(List.of(hat));
        when(inventoryMapper.selectList(any())).thenReturn(List.of());
        when(economyService.mode()).thenReturn(PetEconomyService.Mode.PET);
        when(economyService.balanceOf(100L)).thenReturn(0L);

        PetShopVO vo = shopService.shop(100L);

        assertThat(vo.items()).extracting(PetShopItemVO::code).containsExactly("hat", "apple");
        verify(itemCatalog).toShopItem(eq(hat), eq(false), eq("需要 Lv.8"));
    }

    @Test
    @DisplayName("余额查询异常 Fail-Open：balance=null 不阻断目录，币种仍 PET_COIN")
    void shop_balanceFailOpenKeepsCatalog() {
        when(equipmentConfigMapper.selectList(any())).thenReturn(List.of());
        when(inventoryMapper.selectList(any())).thenReturn(List.of());
        when(economyService.balanceOf(100L)).thenThrow(new RuntimeException("wallet down"));

        PetShopVO vo = shopService.shop(100L);

        assertThat(vo.balance()).isNull();
        assertThat(vo.currency()).isEqualTo("PET_COIN");
        assertThat(vo.items()).extracting(PetShopItemVO::code).containsExactly("apple");
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("STRAWBERRY");
        pet.setLevel(5);
        pet.setEvolutionStage(0);
        return pet;
    }

    private PetEquipmentConfig equipment(String code, int requiredLevel) {
        PetEquipmentConfig config = new PetEquipmentConfig();
        config.setCode(code);
        config.setEnabled(true);
        config.setSort(1);
        config.setRequiredLevel(requiredLevel);
        return config;
    }

    private PetInventory inventory(String itemType, String code) {
        PetInventory item = new PetInventory();
        item.setPetId(1L);
        item.setUserId(100L);
        item.setItemType(itemType);
        item.setItemCode(code);
        item.setQuantity(1);
        item.setEquipped(false);
        return item;
    }

    private PetShopItemVO shopItem(String itemType, String code) {
        return new PetShopItemVO(itemType, code, code, "", "⭐", "COMMON", 100,
                // slot, species, color, accessory, skillType, effect, effectValue
                null, null, null, null, null, null, null,
                // bonus×5
                0, 0, 0, 0, 0,
                // requiredLevel, requiredEvolutionStage, owned, eligible, lockReason
                1, 0, false, false, null);
    }
}
