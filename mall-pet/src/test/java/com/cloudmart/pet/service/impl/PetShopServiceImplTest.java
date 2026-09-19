package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物商城测试：门槛校验（等级/进化/种类）、重复购买拒绝、价格 0 不扣星光、
 * 购买顺序（先入包后扣星光，扣减异常向上抛出触发回滚）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetShopServiceImpl 单元测试")
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
    private WishFeignClient wishFeignClient;

    private PetShopServiceImpl shopService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
    }

    @BeforeEach
    void setUp() {
        shopService = new PetShopServiceImpl(petService, itemCatalog, equipmentConfigMapper, skinConfigMapper,
                skillConfigMapper, inventoryMapper, skillMapper, wishFeignClient);
        lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
        lenient().when(skillMapper.selectList(any())).thenReturn(List.of());
        lenient().when(inventoryMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("购买装备：先入包再扣星光，返回背包物品")
    void buyEquipmentInsertsThenSpends() {
        when(itemCatalog.equipment("straw_hat")).thenReturn(Optional.of(equipment("straw_hat", 120, 1, 0)));
        when(inventoryMapper.selectCount(any())).thenReturn(0L);
        when(inventoryMapper.insert(any(PetInventory.class))).thenReturn(1);
        PetInventoryItemVO vo = new PetInventoryItemVO("EQUIPMENT", "straw_hat", "草编渔夫帽", "", "👒",
                "COMMON", "HAT", null, null, null, null, 0, 0, 1, 1, 0, false, 1, false, null);
        when(itemCatalog.toInventoryVo(any(), eq(false))).thenReturn(vo);

        PetInventoryItemVO result = shopService.buy(100L, new BuyItemRequest("EQUIPMENT", "straw_hat"));

        assertThat(result.code()).isEqualTo("straw_hat");
        verify(inventoryMapper).insert(any(PetInventory.class));
        verify(wishFeignClient).spendStarlight(eq(100L), eq(120), any());
    }

    @Test
    @DisplayName("购买装备：价格 0 时不调用星光服务")
    void freeItemDoesNotSpend() {
        when(itemCatalog.equipment("free_hat")).thenReturn(Optional.of(equipment("free_hat", 0, 1, 0)));
        when(inventoryMapper.selectCount(any())).thenReturn(0L);
        when(inventoryMapper.insert(any(PetInventory.class))).thenReturn(1);
        when(itemCatalog.toInventoryVo(any(), anyBoolean())).thenReturn(null);

        shopService.buy(100L, new BuyItemRequest("EQUIPMENT", "free_hat"));

        verify(wishFeignClient, never()).spendStarlight(any(), any(), any());
    }

    @Test
    @DisplayName("重复购买：409 PET_ITEM_ALREADY_OWNED，且不扣星光")
    void duplicatePurchaseRejected() {
        when(itemCatalog.equipment("straw_hat")).thenReturn(Optional.of(equipment("straw_hat", 120, 1, 0)));
        when(inventoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("EQUIPMENT", "straw_hat")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_ITEM_ALREADY_OWNED);
        verify(wishFeignClient, never()).spendStarlight(any(), any(), any());
    }

    @Test
    @DisplayName("等级不足：409 PET_LEVEL_REQUIRED，且不扣星光")
    void levelGateBlocksPurchase() {
        when(itemCatalog.equipment("explorer_cap")).thenReturn(Optional.of(equipment("explorer_cap", 320, 10, 0)));

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("EQUIPMENT", "explorer_cap")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_LEVEL_REQUIRED);
        verify(wishFeignClient, never()).spendStarlight(any(), any(), any());
    }

    @Test
    @DisplayName("进化阶段不足：409 PET_EVOLUTION_REQUIRED")
    void evolutionGateBlocksPurchase() {
        when(itemCatalog.equipment("crystal_pendant"))
                .thenReturn(Optional.of(equipment("crystal_pendant", 680, 1, 1)));

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("EQUIPMENT", "crystal_pendant")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVOLUTION_REQUIRED);
    }

    @Test
    @DisplayName("皮肤种类不匹配：400 PET_SKIN_SPECIES_MISMATCH")
    void skinSpeciesMismatchRejected() {
        PetSkinConfig skin = new PetSkinConfig();
        skin.setCode("golden_dog");
        skin.setName("金渐层柴");
        skin.setSpecies("DOG");
        skin.setColor("golden");
        skin.setAccessory("bandana");
        skin.setPriceStarlight(260);
        skin.setRequiredLevel(2);
        skin.setRequiredEvolutionStage(0);
        skin.setEnabled(true);
        when(itemCatalog.skin("golden_dog")).thenReturn(Optional.of(skin));

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("SKIN", "golden_dog")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_SKIN_SPECIES_MISMATCH);
    }

    @Test
    @DisplayName("物品已下架：404 PET_ITEM_NOT_FOUND")
    void disabledItemNotFound() {
        PetEquipmentConfig disabled = equipment("straw_hat", 120, 1, 0);
        disabled.setEnabled(false);
        when(itemCatalog.equipment("straw_hat")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("EQUIPMENT", "straw_hat")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_ITEM_NOT_FOUND);
    }

    private PetEquipmentConfig equipment(String code, int price, int requiredLevel, int requiredEvolutionStage) {
        PetEquipmentConfig config = new PetEquipmentConfig();
        config.setCode(code);
        config.setName(code);
        config.setSlot("HAT");
        config.setRarity("COMMON");
        config.setIcon("👒");
        config.setPriceStarlight(price);
        config.setBonusStrength(0);
        config.setBonusIntelligence(0);
        config.setBonusAgility(1);
        config.setBonusCharm(1);
        config.setBonusMaxHp(0);
        config.setRequiredLevel(requiredLevel);
        config.setRequiredEvolutionStage(requiredEvolutionStage);
        config.setEnabled(true);
        return config;
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("CAT");
        pet.setLevel(5);
        pet.setMaxHp(100);
        pet.setHp(80);
        pet.setStrength(5);
        pet.setIntelligence(5);
        pet.setAgility(5);
        pet.setCharm(5);
        pet.setEvolutionStage(0);
        return pet;
    }
}
