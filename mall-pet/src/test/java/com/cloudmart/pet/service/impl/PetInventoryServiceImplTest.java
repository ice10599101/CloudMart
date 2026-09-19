package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.EquipItemRequest;
import com.cloudmart.pet.dto.WearSkinRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.service.PetService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物背包穿戴测试：同部位互斥卸下、未拥有拒绝、皮肤种类校验、
 * 卸下皮肤恢复种类默认外观（appearance/skinCode 双写一致性）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetInventoryServiceImpl 单元测试")
class PetInventoryServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetItemCatalog itemCatalog;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetSkillMapper skillMapper;
    @Mock
    private PetMapper petMapper;

    private PetInventoryServiceImpl inventoryService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
        TableInfoHelper.initTableInfo(assistant, Pet.class);
    }

    @BeforeEach
    void setUp() {
        inventoryService = new PetInventoryServiceImpl(petService, itemCatalog, inventoryMapper,
                skillMapper, petMapper);
        lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
        lenient().when(skillMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("穿戴装备：先卸下同部位旧装备，再标记新装备")
    void equipUnequipsSameSlotFirst() {
        PetInventory item = item("EQUIPMENT", "straw_hat");
        when(inventoryMapper.selectOne(any())).thenReturn(item);
        when(itemCatalog.equipment("straw_hat")).thenReturn(Optional.of(equipment("straw_hat", "HAT", 1, 0)));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.equip(100L, new EquipItemRequest("straw_hat"));

        verify(inventoryMapper).update(any(), any());
        verify(inventoryMapper).updateById(item);
        assertThat(item.getEquipped()).isTrue();
        assertThat(item.getSlot()).isEqualTo("HAT");
        verify(petService).getMyPet(100L);
    }

    @Test
    @DisplayName("未拥有装备：409 PET_ITEM_NOT_OWNED")
    void equipNotOwnedRejected() {
        when(inventoryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> inventoryService.equip(100L, new EquipItemRequest("straw_hat")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_ITEM_NOT_OWNED);
    }

    @Test
    @DisplayName("穿戴皮肤：写入 appearance(color/accessory) 与 skinCode")
    void wearSkinWritesAppearanceAndSkinCode() {
        Pet pet = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        PetInventory item = item("SKIN", "mint_cat");
        when(inventoryMapper.selectOne(any())).thenReturn(item);
        when(itemCatalog.skin("mint_cat")).thenReturn(Optional.of(skin("mint_cat", "CAT", "mint", "bow", 2, 0)));
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.wearSkin(100L, new WearSkinRequest("mint_cat"));

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(petMapper).updateById(captor.capture());
        Pet saved = captor.getValue();
        assertThat(saved.getSkinCode()).isEqualTo("mint_cat");
        assertThat(saved.getAppearance()).contains("mint").contains("bow");
        assertThat(item.getEquipped()).isTrue();
    }

    @Test
    @DisplayName("穿戴皮肤：种类不匹配 400 PET_SKIN_SPECIES_MISMATCH")
    void wearSkinSpeciesMismatch() {
        when(inventoryMapper.selectOne(any())).thenReturn(item("SKIN", "golden_dog"));
        when(itemCatalog.skin("golden_dog")).thenReturn(Optional.of(skin("golden_dog", "DOG", "golden", "bandana", 2, 0)));

        assertThatThrownBy(() -> inventoryService.wearSkin(100L, new WearSkinRequest("golden_dog")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_SKIN_SPECIES_MISMATCH);
    }

    @Test
    @DisplayName("穿戴皮肤：进化阶段不足 409 PET_EVOLUTION_REQUIRED")
    void wearSkinEvolutionGate() {
        when(inventoryMapper.selectOne(any())).thenReturn(item("SKIN", "aurora_legend"));
        when(itemCatalog.skin("aurora_legend"))
                .thenReturn(Optional.of(skin("aurora_legend", null, "aurora", "crown", 1, 1)));

        assertThatThrownBy(() -> inventoryService.wearSkin(100L, new WearSkinRequest("aurora_legend")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVOLUTION_REQUIRED);
    }

    @Test
    @DisplayName("卸下皮肤：恢复种类默认色并清空 skinCode")
    void removeSkinRestoresSpeciesColor() {
        Pet pet = pet();
        pet.setSkinCode("mint_cat");
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(inventoryMapper.update(any(), any())).thenReturn(1);

        inventoryService.removeSkin(100L);

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(petMapper, atLeastOnce()).updateById(captor.capture());
        Pet saved = captor.getValue();
        assertThat(saved.getSkinCode()).isNull();
        assertThat(saved.getAppearance()).contains("orange").contains("none");
    }

    @Test
    @DisplayName("卸下装备：非法部位 400 PET_VALIDATION_ERROR")
    void unequipInvalidSlotRejected() {
        assertThatThrownBy(() -> inventoryService.unequip(100L, "TAIL"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VALIDATION_ERROR);
    }

    @Test
    @DisplayName("卸下装备：该部位无装备 409 PET_ITEM_NOT_OWNED")
    void unequipNothingRejected() {
        when(inventoryMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.unequip(100L, "hat"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_ITEM_NOT_OWNED);
    }

    private PetInventory item(String type, String code) {
        PetInventory item = new PetInventory();
        item.setId(9L);
        item.setPetId(1L);
        item.setUserId(100L);
        item.setItemType(type);
        item.setItemCode(code);
        item.setQuantity(1);
        item.setEquipped(false);
        return item;
    }

    private PetEquipmentConfig equipment(String code, String slot, int level, int evolutionStage) {
        PetEquipmentConfig config = new PetEquipmentConfig();
        config.setCode(code);
        config.setName(code);
        config.setSlot(slot);
        config.setRequiredLevel(level);
        config.setRequiredEvolutionStage(evolutionStage);
        return config;
    }

    private PetSkinConfig skin(String code, String species, String color, String accessory,
                               int level, int evolutionStage) {
        PetSkinConfig config = new PetSkinConfig();
        config.setCode(code);
        config.setName(code);
        config.setSpecies(species);
        config.setColor(color);
        config.setAccessory(accessory);
        config.setRequiredLevel(level);
        config.setRequiredEvolutionStage(evolutionStage);
        return config;
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("CAT");
        pet.setLevel(5);
        pet.setEvolutionStage(0);
        pet.setMaxHp(100);
        pet.setHp(80);
        return pet;
    }
}
