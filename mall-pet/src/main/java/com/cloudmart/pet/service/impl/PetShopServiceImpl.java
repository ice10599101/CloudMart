package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetShopService;
import com.cloudmart.pet.vo.PetShopItemVO;
import com.cloudmart.pet.vo.PetShopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宠物商城目录实现（C13：购买已统一到 {@code POST /pet/purchases}，本类仅负责目录装配）。
 *
 * <p>装配口径：装备/皮肤/技能书取启用配置并按拥有/已学状态与门槛生成锁定原因；
 * 食物取配置目录可重复购买；余额查询 Fail-Open（null=前端隐藏余额，不阻断浏览）。</p>
 */
@Service
@Slf4j
public class PetShopServiceImpl implements PetShopService {

    private final PetService petService;
    private final PetItemCatalog itemCatalog;
    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkinConfigMapper skinConfigMapper;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetSkillMapper skillMapper;
    private final PetEconomyService economyService;

    public PetShopServiceImpl(PetService petService,
                              PetItemCatalog itemCatalog,
                              PetEquipmentConfigMapper equipmentConfigMapper,
                              PetSkinConfigMapper skinConfigMapper,
                              PetSkillConfigMapper skillConfigMapper,
                              PetInventoryMapper inventoryMapper,
                              PetSkillMapper skillMapper,
                              PetEconomyService economyService) {
        this.petService = petService;
        this.itemCatalog = itemCatalog;
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skinConfigMapper = skinConfigMapper;
        this.skillConfigMapper = skillConfigMapper;
        this.inventoryMapper = inventoryMapper;
        this.skillMapper = skillMapper;
        this.economyService = economyService;
    }

    @Override
    public PetShopVO shop(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        Map<PetItemType, Set<String>> owned = ownedCodesByType(pet.getId());
        Set<String> learnedSkills = learnedSkillCodes(pet.getId());

        List<PetShopItemVO> items = new ArrayList<>();
        equipmentConfigMapper.selectList(new LambdaQueryWrapper<PetEquipmentConfig>()
                        .eq(PetEquipmentConfig::getEnabled, true)
                        .orderByAsc(PetEquipmentConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        owned.getOrDefault(PetItemType.EQUIPMENT, Set.of()).contains(config.getCode()),
                        equipmentLockReason(pet, config))));
        skinConfigMapper.selectList(new LambdaQueryWrapper<PetSkinConfig>()
                        .eq(PetSkinConfig::getEnabled, true)
                        .orderByAsc(PetSkinConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        owned.getOrDefault(PetItemType.SKIN, Set.of()).contains(config.getCode()),
                        skinLockReason(pet, config))));
        skillConfigMapper.selectList(new LambdaQueryWrapper<PetSkillConfig>()
                        .eq(PetSkillConfig::getEnabled, true)
                        .orderByAsc(PetSkillConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        learnedSkills.contains(config.getCode())
                                || owned.getOrDefault(PetItemType.SKILL_BOOK, Set.of()).contains(config.getCode()),
                        skillLockReason(pet, config))));
        // F1：食物道具上架（配置表，可重复购买堆叠入包）
        for (PetItemCatalog.FoodItem food : itemCatalog.listFoods()) {
            items.add(new PetShopItemVO(PetItemType.FOOD.name(), food.code(), food.name(),
                    food.description(), food.icon(), "COMMON", food.priceCoins(),
                    null, null, null, null, null, null, null,
                    0, 0, 0, 0, 0,
                    1, 0,
                    false, true, null));
        }
        // 维护窗口（PAUSED）时余额为 null：币种仍标 PET_COIN，前端隐藏余额但可浏览目录
        String currency = "PET_COIN";
        return new PetShopVO(balanceQuietly(userId), currency, items);
    }

    private String equipmentLockReason(Pet pet, PetEquipmentConfig config) {
        int level = config.getRequiredLevel() != null ? config.getRequiredLevel() : 1;
        if (pet.getLevel() < level) {
            return "需要 Lv." + level;
        }
        int stage = config.getRequiredEvolutionStage() != null ? config.getRequiredEvolutionStage() : 0;
        if ((pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) < stage) {
            return "需要进化 " + stage + " 阶";
        }
        return null;
    }

    private String skinLockReason(Pet pet, PetSkinConfig config) {
        if (config.getSpecies() != null && !config.getSpecies().isBlank()
                && !config.getSpecies().equals(pet.getSpecies())) {
            return "限定 " + config.getSpecies() + " 种类";
        }
        return equipmentStageReason(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());
    }

    private String skillLockReason(Pet pet, PetSkillConfig config) {
        return equipmentStageReason(pet, config.getRequiredLevel(), 0);
    }

    private String equipmentStageReason(Pet pet, Integer requiredLevel, Integer requiredEvolutionStage) {
        int level = requiredLevel != null ? requiredLevel : 1;
        if (pet.getLevel() < level) {
            return "需要 Lv." + level;
        }
        int stage = requiredEvolutionStage != null ? requiredEvolutionStage : 0;
        if ((pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) < stage) {
            return "需要进化 " + stage + " 阶";
        }
        return null;
    }

    /** 按 (petId, itemType, itemCode) 分型聚合拥有集合（B12：装备/皮肤/技能书编码空间独立） */
    private Map<PetItemType, Set<String>> ownedCodesByType(Long petId) {
        Map<PetItemType, Set<String>> owned = new HashMap<>();
        inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, petId))
                .forEach(item -> owned
                        .computeIfAbsent(PetItemType.valueOf(item.getItemType()), type -> new HashSet<>())
                        .add(item.getItemCode()));
        return owned;
    }

    private Set<String> learnedSkillCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        skillMapper.selectList(new LambdaQueryWrapper<PetSkill>()
                        .eq(PetSkill::getPetId, petId))
                .forEach(skill -> codes.add(skill.getSkillCode()));
        return codes;
    }

    /** 余额查询：展示型数据 Fail-Open（null=前端隐藏余额，不阻断商城浏览） */
    private Long balanceQuietly(Long userId) {
        try {
            return economyService.balanceOf(userId);
        } catch (Exception e) {
            log.warn("宠物币余额查询降级（Fail-Open）: userId={}", userId, e);
            return null;
        }
    }
}
