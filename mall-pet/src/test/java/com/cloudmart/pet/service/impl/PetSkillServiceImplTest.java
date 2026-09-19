package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.LearnSkillRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.service.PetService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物技能测试：学习必须先有技能书、重复学习拒绝、效果文案服务端生成（百分比/点数）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetSkillServiceImpl 单元测试")
class PetSkillServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetItemCatalog itemCatalog;
    @Mock
    private PetSkillConfigMapper skillConfigMapper;
    @Mock
    private PetSkillMapper skillMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;

    private PetSkillServiceImpl skillService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetSkill.class);
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
    }

    @BeforeEach
    void setUp() {
        skillService = new PetSkillServiceImpl(petService, itemCatalog, skillConfigMapper,
                skillMapper, inventoryMapper);
        lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
        lenient().when(skillMapper.selectList(any())).thenReturn(List.of());
        lenient().when(inventoryMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("学习技能：背包没有技能书 → 409 PET_SKILL_BOOK_REQUIRED")
    void learnRequiresBook() {
        when(itemCatalog.skill("lucky_fish")).thenReturn(Optional.of(config("lucky_fish", "PASSIVE", 0.08, 2)));

        assertThatThrownBy(() -> skillService.learn(100L, new LearnSkillRequest("lucky_fish")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_SKILL_BOOK_REQUIRED);
    }

    @Test
    @DisplayName("学习技能：已有技能书且等级满足 → 落 pet_skill 记录")
    void learnInsertsSkillWhenBookOwned() {
        when(itemCatalog.skill("lucky_fish")).thenReturn(Optional.of(config("lucky_fish", "PASSIVE", 0.08, 2)));
        when(inventoryMapper.selectList(any())).thenReturn(List.of(book("lucky_fish")));

        var vo = skillService.learn(100L, new LearnSkillRequest("lucky_fish"));

        assertThat(vo.learned()).isTrue();
        assertThat(vo.equipped()).isTrue();
        verify(skillMapper).insert(any(PetSkill.class));
    }

    @Test
    @DisplayName("重复学习：409 PET_SKILL_ALREADY_LEARNED")
    void duplicateLearnRejected() {
        when(itemCatalog.skill("lucky_fish")).thenReturn(Optional.of(config("lucky_fish", "PASSIVE", 0.08, 2)));
        PetSkill learned = new PetSkill();
        learned.setPetId(1L);
        learned.setSkillCode("lucky_fish");
        when(skillMapper.selectList(any())).thenReturn(List.of(learned));

        assertThatThrownBy(() -> skillService.learn(100L, new LearnSkillRequest("lucky_fish")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_SKILL_ALREADY_LEARNED);
    }

    @Test
    @DisplayName("等级不足：409 PET_LEVEL_REQUIRED")
    void levelGateBlocksLearn() {
        when(itemCatalog.skill("tough_body")).thenReturn(Optional.of(config("tough_body", "PASSIVE", 0.12, 20)));
        when(inventoryMapper.selectList(any())).thenReturn(List.of(book("tough_body")));

        assertThatThrownBy(() -> skillService.learn(100L, new LearnSkillRequest("tough_body")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_LEVEL_REQUIRED);
    }

    @Test
    @DisplayName("效果文案：百分比与点数两套口径（服务端统一生成）")
    void effectTextFormats() {
        assertThat(PetSkillServiceImpl.effectText("POWER_STRIKE", new BigDecimal("0.350"))).isEqualTo("首回合伤害 +35%");
        assertThat(PetSkillServiceImpl.effectText("LUCKY_FISH", new BigDecimal("0.080"))).isEqualTo("捞瓶成功率 +8%");
        assertThat(PetSkillServiceImpl.effectText("QUICK_STEP", new BigDecimal("3.000"))).isEqualTo("战斗先手敏捷 +3");
        assertThat(PetSkillServiceImpl.effectText("TOUGH_BODY", new BigDecimal("0.120"))).isEqualTo("受伤减免 12%");
    }

    private PetInventory book(String code) {
        PetInventory item = new PetInventory();
        item.setPetId(1L);
        item.setItemType("SKILL_BOOK");
        item.setItemCode(code);
        item.setQuantity(1);
        item.setEquipped(false);
        return item;
    }

    private PetSkillConfig config(String code, String type, double value, int requiredLevel) {
        PetSkillConfig config = new PetSkillConfig();
        config.setCode(code);
        config.setName(code);
        config.setSkillType(type);
        config.setEffect("LUCKY_FISH");
        config.setEffectValue(BigDecimal.valueOf(value));
        config.setPriceStarlight(100);
        config.setRequiredLevel(requiredLevel);
        config.setEnabled(true);
        return config;
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setLevel(5);
        pet.setEvolutionStage(0);
        return pet;
    }
}
