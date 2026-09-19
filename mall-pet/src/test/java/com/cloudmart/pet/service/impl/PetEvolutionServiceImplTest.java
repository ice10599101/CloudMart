package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetEvolutionConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetEvolutionConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetEvolutionVO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物进化测试：等级门槛、满阶拒绝、成功后属性/阶段/皮肤入包、星光不足的只读判定。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetEvolutionServiceImpl 单元测试")
class PetEvolutionServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetEvolutionConfigMapper evolutionConfigMapper;
    @Mock
    private PetMapper petMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetEventProducer eventProducer;

    private PetEvolutionServiceImpl evolutionService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
    }

    @BeforeEach
    void setUp() {
        evolutionService = new PetEvolutionServiceImpl(petService, evolutionConfigMapper, petMapper,
                inventoryMapper, activityMapper, wishFeignClient, achievementService, eventProducer);
        lenient().when(inventoryMapper.insert(any(PetInventory.class))).thenReturn(1);
        lenient().when(activityMapper.insert(any(PetActivity.class))).thenReturn(1);
        lenient().when(wishFeignClient.starlightBalance(100L)).thenReturn(ApiResponse.ok(5000));
    }

    @Test
    @DisplayName("进化成功：阶段推进 + 属性提升 + 星光扣减 + 皮肤入包 + 成就评估")
    void evolveAppliesBonusesAndSpends() {
        Pet pet = pet(10, 0);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        PetEvolutionVO result = evolutionService.evolve(100L);

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(petMapper).updateById(captor.capture());
        Pet saved = captor.getValue();
        assertThat(saved.getEvolutionStage()).isEqualTo(1);
        assertThat(saved.getMaxHp()).isEqualTo(125);
        assertThat(saved.getStrength()).isEqualTo(8);
        assertThat(saved.getCharm()).isEqualTo(8);
        assertThat(result.currentStage()).isEqualTo(1);
        verify(wishFeignClient).spendStarlight(eq(100L), eq(600), any());
        verify(inventoryMapper).insert(any(PetInventory.class));
        verify(achievementService).evaluate(pet, PetAchievementService.Event.EVOLUTION);
    }

    @Test
    @DisplayName("等级不足：409 PET_LEVEL_REQUIRED，不扣星光")
    void levelGateBlocksEvolution() {
        Pet pet = pet(3, 0);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        assertThatThrownBy(() -> evolutionService.evolve(100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_LEVEL_REQUIRED);
        verify(wishFeignClient, never()).spendStarlight(any(), any(), any());
    }

    @Test
    @DisplayName("已到最高阶：409 PET_EVOLUTION_MAX")
    void maxStageRejected() {
        Pet pet = pet(30, 2);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        assertThatThrownBy(() -> evolutionService.evolve(100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVOLUTION_MAX);
    }

    @Test
    @DisplayName("只读状态：星光不足 → canEvolve=false 且给出可展示原因")
    void statusReportsInsufficientStarlight() {
        Pet pet = pet(10, 0);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));
        when(wishFeignClient.starlightBalance(100L)).thenReturn(ApiResponse.ok(100));

        PetEvolutionVO status = evolutionService.status(100L);

        assertThat(status.canEvolve()).isFalse();
        assertThat(status.lockReason()).contains("星光不足");
    }

    @Test
    @DisplayName("只读状态：达到最高阶 → nextCode 为空且提示已满阶")
    void statusMaxStage() {
        Pet pet = pet(30, 2);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        PetEvolutionVO status = evolutionService.status(100L);

        assertThat(status.nextCode()).isNull();
        assertThat(status.maxStage()).isEqualTo(1);
        assertThat(status.canEvolve()).isFalse();
    }

    private PetEvolutionConfig evolve1() {
        PetEvolutionConfig config = new PetEvolutionConfig();
        config.setCode("evolve_1");
        config.setName("初阶进化");
        config.setDescription("毛发泛起微光");
        config.setStageFrom(0);
        config.setStageTo(1);
        config.setRequiredLevel(8);
        config.setCostStarlight(600);
        config.setBonusMaxHp(25);
        config.setBonusStrength(3);
        config.setBonusIntelligence(3);
        config.setBonusAgility(3);
        config.setBonusCharm(3);
        config.setUnlockSkinCode("aurora_legend");
        config.setEnabled(true);
        return config;
    }

    private Pet pet(int level, int evolutionStage) {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("CAT");
        pet.setLevel(level);
        pet.setEvolutionStage(evolutionStage);
        pet.setMaxHp(100);
        pet.setHp(90);
        pet.setStrength(5);
        pet.setIntelligence(5);
        pet.setAgility(5);
        pet.setCharm(5);
        return pet;
    }
}
