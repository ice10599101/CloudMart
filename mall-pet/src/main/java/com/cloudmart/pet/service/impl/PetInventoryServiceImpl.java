package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.EquipItemRequest;
import com.cloudmart.pet.dto.WearSkinRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetEquipmentSlot;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.service.PetInventoryService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宠物背包与穿戴实现。
 *
 * <p>互斥规则由服务端保证：同一部位至多一件装备、同类型至多一套皮肤处于
 * {@code equipped=1}——写入前先批量卸下，避免前端状态不一致导致"穿戴两件"。</p>
 */
@Service
public class PetInventoryServiceImpl implements PetInventoryService {

    private static final Set<String> VALID_SLOTS = new HashSet<>(
            java.util.Arrays.stream(PetEquipmentSlot.values()).map(Enum::name).toList());

    private final PetService petService;
    private final PetItemCatalog itemCatalog;
    private final PetInventoryMapper inventoryMapper;
    private final PetSkillMapper skillMapper;
    private final PetMapper petMapper;

    public PetInventoryServiceImpl(PetService petService,
                                   PetItemCatalog itemCatalog,
                                   PetInventoryMapper inventoryMapper,
                                   PetSkillMapper skillMapper,
                                   PetMapper petMapper) {
        this.petService = petService;
        this.itemCatalog = itemCatalog;
        this.inventoryMapper = inventoryMapper;
        this.skillMapper = skillMapper;
        this.petMapper = petMapper;
    }

    @Override
    public List<PetInventoryItemVO> inventory(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        Set<String> learnedSkills = learnedSkillCodes(pet.getId());
        return inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, pet.getId()))
                .stream()
                .sorted(Comparator.comparing(PetInventory::getItemType)
                        .thenComparing(item -> item.getSlot() != null ? item.getSlot() : ""))
                .map(item -> itemCatalog.toInventoryVo(item,
                        PetItemType.SKILL_BOOK.name().equals(item.getItemType())
                                && learnedSkills.contains(item.getItemCode())))
                .toList();
    }

    @Override
    @Transactional
    public PetVO equip(Long userId, EquipItemRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetInventory item = requireItem(pet.getId(), PetItemType.EQUIPMENT, request.itemCode());
        PetEquipmentConfig config = itemCatalog.equipment(request.itemCode())
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_ITEM_NOT_FOUND,
                        "这件装备不存在或已下架"));
        requireWearable(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());

        inventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                .set(PetInventory::getEquipped, false)
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, PetItemType.EQUIPMENT.name())
                .eq(PetInventory::getSlot, config.getSlot()));
        item.setEquipped(true);
        item.setSlot(config.getSlot());
        inventoryMapper.updateById(item);
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO unequip(Long userId, String slot) {
        Pet pet = petService.requireOwnedPet(userId);
        String normalized = slot != null ? slot.toUpperCase() : "";
        if (!VALID_SLOTS.contains(normalized)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "装备部位非法");
        }
        int updated = inventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                .set(PetInventory::getEquipped, false)
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, PetItemType.EQUIPMENT.name())
                .eq(PetInventory::getSlot, normalized)
                .eq(PetInventory::getEquipped, true));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_NOT_OWNED, "这个部位没有穿戴装备");
        }
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO wearSkin(Long userId, WearSkinRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetInventory item = requireItem(pet.getId(), PetItemType.SKIN, request.skinCode());
        PetSkinConfig config = itemCatalog.skin(request.skinCode())
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_ITEM_NOT_FOUND,
                        "这套皮肤不存在或已下架"));
        if (config.getSpecies() != null && !config.getSpecies().isBlank()
                && !config.getSpecies().equals(pet.getSpecies())) {
            throw new BusinessException(PetErrorCodes.PET_SKIN_SPECIES_MISMATCH,
                    "这套皮肤只适合 " + config.getSpecies() + " 种类");
        }
        requireWearable(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());

        inventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                .set(PetInventory::getEquipped, false)
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, PetItemType.SKIN.name()));
        item.setEquipped(true);
        inventoryMapper.updateById(item);

        pet.setSkinCode(config.getCode());
        pet.setAppearance(PetJsonUtils.toJson(Map.of(
                "color", config.getColor(),
                "accessory", config.getAccessory() != null ? config.getAccessory() : PetItemCatalog.defaultAccessory())));
        petMapper.updateById(pet);
        return petService.getMyPet(userId);
    }

    @Override
    @Transactional
    public PetVO removeSkin(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        inventoryMapper.update(null, new LambdaUpdateWrapper<PetInventory>()
                .set(PetInventory::getEquipped, false)
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, PetItemType.SKIN.name()));
        if (pet.getSkinCode() == null) {
            return petService.getMyPet(userId);
        }
        pet.setSkinCode(null);
        pet.setAppearance(PetJsonUtils.toJson(Map.of(
                "color", PetItemCatalog.defaultColorFor(pet.getSpecies()),
                "accessory", PetItemCatalog.defaultAccessory())));
        petMapper.updateById(pet);
        return petService.getMyPet(userId);
    }

    private PetInventory requireItem(Long petId, PetItemType type, String code) {
        PetInventory item = inventoryMapper.selectOne(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, petId)
                .eq(PetInventory::getItemType, type.name())
                .eq(PetInventory::getItemCode, code)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_NOT_OWNED, "背包里还没有这个物品，先去商城看看吧");
        }
        return item;
    }

    private void requireWearable(Pet pet, Integer requiredLevel, Integer requiredEvolutionStage) {
        int level = requiredLevel != null ? requiredLevel : 1;
        if (pet.getLevel() < level) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED, "等级达到 Lv." + level + " 才能使用哦");
        }
        int stage = requiredEvolutionStage != null ? requiredEvolutionStage : 0;
        if ((pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) < stage) {
            throw new BusinessException(PetErrorCodes.PET_EVOLUTION_REQUIRED, "需要先完成进化才能使用");
        }
    }

    private Set<String> learnedSkillCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        skillMapper.selectList(new LambdaQueryWrapper<PetSkill>()
                        .eq(PetSkill::getPetId, petId))
                .forEach(skill -> codes.add(skill.getSkillCode()));
        return codes;
    }
}
