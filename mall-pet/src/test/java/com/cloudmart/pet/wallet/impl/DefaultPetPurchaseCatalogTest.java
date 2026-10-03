package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetFoodConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetFoodConfigMapper;
import com.cloudmart.pet.repository.PetFurnitureConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * R02 购买目录测试（门槛校验从旧 PetShopServiceImpl 上收）：
 * 补 FOOD 目录、上下架/等级/进化/种类门槛、按宠物唯一性判定（原实现按 userId 全账号误判）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultPetPurchaseCatalog 目录门槛测试（R02）")
class DefaultPetPurchaseCatalogTest {

    @Mock
    private PetEquipmentConfigMapper equipmentConfigMapper;
    @Mock
    private PetSkinConfigMapper skinConfigMapper;
    @Mock
    private PetSkillConfigMapper skillConfigMapper;
    @Mock
    private PetFurnitureConfigMapper furnitureConfigMapper;
    @Mock
    private PetFoodConfigMapper foodConfigMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetMapper petMapper;

    private DefaultPetPurchaseCatalog catalog;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
        TableInfoHelper.initTableInfo(assistant, PetEquipmentConfig.class);
        TableInfoHelper.initTableInfo(assistant, PetSkinConfig.class);
        TableInfoHelper.initTableInfo(assistant, PetFoodConfig.class);
    }

    @BeforeEach
    void setUp() {
        catalog = new DefaultPetPurchaseCatalog(equipmentConfigMapper, skinConfigMapper, skillConfigMapper,
                furnitureConfigMapper, foodConfigMapper, inventoryMapper, petMapper);
        lenient().when(petMapper.selectById(1L)).thenReturn(pet());
        lenient().when(inventoryMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("R02 补 FOOD：目录可加载食物并给出服务端权威价格快照")
    void loadsFood() {
        when(foodConfigMapper.selectOne(any())).thenReturn(food("apple", 20, 1));

        var entry = catalog.load(100L, 1L, "FOOD", "apple", null);

        assertThat(entry.itemType()).isEqualTo("FOOD");
        assertThat(entry.unitPrice()).isEqualTo(20);
        // 食物可重复购买：不属于唯一类
        assertThat(catalog.isUniquePerPet("FOOD")).isFalse();
    }

    @Test
    @DisplayName("R02 FOOD 下架/缺失：404 PET_ITEM_NOT_FOUND")
    void disabledFoodNotFound() {
        when(foodConfigMapper.selectOne(any())).thenReturn(food("apple", 20, 0));

        assertThatThrownBy(() -> catalog.load(100L, 1L, "FOOD", "apple", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("等级门槛上收：等级不足 409 PET_LEVEL_REQUIRED")
    void levelGateBlocks() {
        when(equipmentConfigMapper.selectOne(any())).thenReturn(equipment("explorer_cap", 320, 10, 0));

        assertThatThrownBy(() -> catalog.load(100L, 1L, "EQUIPMENT", "explorer_cap", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_LEVEL_REQUIRED);
    }

    @Test
    @DisplayName("进化门槛上收：阶段不足 409 PET_EVOLUTION_REQUIRED")
    void evolutionGateBlocks() {
        when(equipmentConfigMapper.selectOne(any())).thenReturn(equipment("crystal_pendant", 680, 1, 1));

        assertThatThrownBy(() -> catalog.load(100L, 1L, "EQUIPMENT", "crystal_pendant", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVOLUTION_REQUIRED);
    }

    @Test
    @DisplayName("种类门槛上收：皮肤种类不匹配 400 PET_SKIN_SPECIES_MISMATCH")
    void skinSpeciesMismatch() {
        PetSkinConfig skin = new PetSkinConfig();
        skin.setCode("golden_dog");
        skin.setName("金渐层柴");
        skin.setSpecies("ORANGE");
        skin.setPriceStarlight(260);
        skin.setRequiredLevel(2);
        skin.setRequiredEvolutionStage(0);
        skin.setEnabled(true);
        when(skinConfigMapper.selectOne(any())).thenReturn(skin);

        assertThatThrownBy(() -> catalog.load(100L, 1L, "SKIN", "golden_dog", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_SKIN_SPECIES_MISMATCH);
    }

    @Test
    @DisplayName("下架商品拒绝：404 PET_ITEM_NOT_FOUND（原实现仅查价格，下架仍可扣款）")
    void disabledEquipmentNotFound() {
        PetEquipmentConfig disabled = equipment("straw_hat", 120, 1, 0);
        disabled.setEnabled(false);
        when(equipmentConfigMapper.selectOne(any())).thenReturn(disabled);

        assertThatThrownBy(() -> catalog.load(100L, 1L, "EQUIPMENT", "straw_hat", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("R02 唯一性按宠物判定：另一宠拥有不影响本宠购买（原实现按 userId 误判）")
    void ownershipIsPerPet() {
        when(equipmentConfigMapper.selectOne(any())).thenReturn(equipment("straw_hat", 120, 1, 0));
        // 该宠物未拥有 → 放行
        when(inventoryMapper.selectCount(any())).thenReturn(0L);

        var entry = catalog.load(100L, 1L, "EQUIPMENT", "straw_hat", null);
        assertThat(entry.itemCode()).isEqualTo("straw_hat");
        // 该宠物已拥有 → AlreadyOwned
        when(inventoryMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> catalog.load(100L, 1L, "EQUIPMENT", "straw_hat", null))
                .isInstanceOf(PetPurchaseCatalog.AlreadyOwnedException.class);
    }

    @Test
    @DisplayName("版本冲突：expectedConfigVersion 不匹配 409 PET_CONFIG_VERSION_CONFLICT")
    void versionConflictRejected() {
        when(equipmentConfigMapper.selectOne(any())).thenReturn(equipment("straw_hat", 120, 1, 0));
        var entry = catalog.load(100L, 1L, "EQUIPMENT", "straw_hat", null);

        assertThatThrownBy(() -> catalog.load(100L, 1L, "EQUIPMENT", "straw_hat",
                entry.configVersion() + "-stale"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_CONFIG_VERSION_CONFLICT);
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

    private PetFoodConfig food(String code, int price, int enabled) {
        PetFoodConfig config = new PetFoodConfig();
        config.setCode(code);
        config.setName(code);
        config.setPriceStarlight(price);
        config.setEnabled(enabled);
        return config;
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("STRAWBERRY");
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
