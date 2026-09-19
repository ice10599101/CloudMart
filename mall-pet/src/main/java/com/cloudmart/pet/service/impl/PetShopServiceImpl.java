package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetShopService;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetShopItemVO;
import com.cloudmart.pet.vo.PetShopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 宠物商城实现。
 *
 * <p>购买顺序：<b>先本地入包，再扣星光</b>——星光扣减失败（余额不足 402 / 服务降级 503）
 * 抛异常回滚本地事务，物品不会产出；反向顺序（先扣钱后入包）在入包失败时会出现
 * "付了钱没拿到东西"的补偿难题（AGENTS §17 数据完整性优先）。</p>
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
    private final WishFeignClient wishFeignClient;

    public PetShopServiceImpl(PetService petService,
                              PetItemCatalog itemCatalog,
                              PetEquipmentConfigMapper equipmentConfigMapper,
                              PetSkinConfigMapper skinConfigMapper,
                              PetSkillConfigMapper skillConfigMapper,
                              PetInventoryMapper inventoryMapper,
                              PetSkillMapper skillMapper,
                              WishFeignClient wishFeignClient) {
        this.petService = petService;
        this.itemCatalog = itemCatalog;
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skinConfigMapper = skinConfigMapper;
        this.skillConfigMapper = skillConfigMapper;
        this.inventoryMapper = inventoryMapper;
        this.skillMapper = skillMapper;
        this.wishFeignClient = wishFeignClient;
    }

    @Override
    public PetShopVO shop(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        Set<String> ownedCodes = ownedCodes(pet.getId());
        Set<String> learnedSkills = learnedSkillCodes(pet.getId());

        List<PetShopItemVO> items = new ArrayList<>();
        equipmentConfigMapper.selectList(new LambdaQueryWrapper<PetEquipmentConfig>()
                        .eq(PetEquipmentConfig::getEnabled, true)
                        .orderByAsc(PetEquipmentConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        ownedCodes.contains(config.getCode()), equipmentLockReason(pet, config))));
        skinConfigMapper.selectList(new LambdaQueryWrapper<PetSkinConfig>()
                        .eq(PetSkinConfig::getEnabled, true)
                        .orderByAsc(PetSkinConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        ownedCodes.contains(config.getCode()), skinLockReason(pet, config))));
        skillConfigMapper.selectList(new LambdaQueryWrapper<PetSkillConfig>()
                        .eq(PetSkillConfig::getEnabled, true)
                        .orderByAsc(PetSkillConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        learnedSkills.contains(config.getCode()), skillLockReason(pet, config))));
        return new PetShopVO(starlightBalanceQuietly(userId), items);
    }

    @Override
    @Transactional
    public PetInventoryItemVO buy(Long userId, BuyItemRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        return switch (PetItemType.valueOf(request.itemType())) {
            case EQUIPMENT -> buyEquipment(pet, request.itemCode());
            case SKIN -> buySkin(pet, request.itemCode());
            case SKILL_BOOK -> buySkillBook(pet, request.itemCode());
        };
    }

    private PetInventoryItemVO buyEquipment(Pet pet, String code) {
        PetEquipmentConfig config = itemCatalog.equipment(code)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_ITEM_NOT_FOUND,
                        "这件装备不存在或已下架"));
        if (owned(pet.getId(), PetItemType.EQUIPMENT, code)) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "背包里已经有这件装备啦");
        }
        requireEligible(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());
        PetInventory item = insertInventory(pet, PetItemType.EQUIPMENT, code, config.getSlot());
        spendStarlight(pet.getUserId(), config.getPriceStarlight(), item.getId());
        return itemCatalog.toInventoryVo(item, false);
    }

    private PetInventoryItemVO buySkin(Pet pet, String code) {
        PetSkinConfig config = itemCatalog.skin(code)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_ITEM_NOT_FOUND,
                        "这套皮肤不存在或已下架"));
        if (owned(pet.getId(), PetItemType.SKIN, code)) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "衣柜里已经有这套皮肤啦");
        }
        requireEligible(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());
        requireSpeciesMatch(pet, config);
        PetInventory item = insertInventory(pet, PetItemType.SKIN, code, null);
        spendStarlight(pet.getUserId(), config.getPriceStarlight(), item.getId());
        return itemCatalog.toInventoryVo(item, false);
    }

    private PetInventoryItemVO buySkillBook(Pet pet, String code) {
        PetSkillConfig config = itemCatalog.skill(code)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_SKILL_NOT_FOUND,
                        "这个技能不存在或已下架"));
        if (learnedSkillCodes(pet.getId()).contains(code)) {
            throw new BusinessException(PetErrorCodes.PET_SKILL_ALREADY_LEARNED, "这个技能已经学会啦");
        }
        requireEligible(pet, config.getRequiredLevel(), 0);
        PetInventory item = insertInventory(pet, PetItemType.SKILL_BOOK, code, null);
        spendStarlight(pet.getUserId(), config.getPriceStarlight(), item.getId());
        return itemCatalog.toInventoryVo(item, false);
    }

    private PetInventory insertInventory(Pet pet, PetItemType type, String code, String slot) {
        PetInventory item = new PetInventory();
        item.setPetId(pet.getId());
        item.setUserId(pet.getUserId());
        item.setItemType(type.name());
        item.setItemCode(code);
        item.setQuantity(1);
        item.setEquipped(false);
        item.setSlot(slot);
        item.setAcquiredAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            inventoryMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 并发重复购买：uk_pet_inventory_item 兜底
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "已经拥有这个物品啦");
        }
        return item;
    }

    /** 扣星光（价格 0 直接跳过）；余额不足/服务降级由 Feign 抛出，事务回滚即撤销入包 */
    private void spendStarlight(Long userId, Integer price, Long refId) {
        int cost = price != null ? price : 0;
        if (cost <= 0) {
            return;
        }
        wishFeignClient.spendStarlight(userId, cost, refId);
    }

    private void requireEligible(Pet pet, Integer requiredLevel, Integer requiredEvolutionStage) {
        int level = requiredLevel != null ? requiredLevel : 1;
        if (pet.getLevel() < level) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED, "等级达到 Lv." + level + " 才能购买哦");
        }
        int stage = requiredEvolutionStage != null ? requiredEvolutionStage : 0;
        int currentStage = pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0;
        if (currentStage < stage) {
            throw new BusinessException(PetErrorCodes.PET_EVOLUTION_REQUIRED, "需要先完成进化才能购买");
        }
    }

    private void requireSpeciesMatch(Pet pet, PetSkinConfig config) {
        if (config.getSpecies() != null && !config.getSpecies().isBlank()
                && !config.getSpecies().equals(pet.getSpecies())) {
            throw new BusinessException(PetErrorCodes.PET_SKIN_SPECIES_MISMATCH,
                    "这套皮肤只适合 " + config.getSpecies() + " 种类");
        }
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

    private boolean owned(Long petId, PetItemType type, String code) {
        return inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, petId)
                .eq(PetInventory::getItemType, type.name())
                .eq(PetInventory::getItemCode, code)) > 0;
    }

    private Set<String> ownedCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, petId))
                .forEach(item -> codes.add(item.getItemType() + ":" + item.getItemCode()));
        return codes;
    }

    private Set<String> learnedSkillCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        skillMapper.selectList(new LambdaQueryWrapper<PetSkill>()
                        .eq(PetSkill::getPetId, petId))
                .forEach(skill -> codes.add(skill.getSkillCode()));
        return codes;
    }

    /** 余额查询：展示型数据 Fail-Open（null=前端隐藏余额，不阻断商城浏览） */
    private Integer starlightBalanceQuietly(Long userId) {
        try {
            return wishFeignClient.starlightBalance(userId).data();
        } catch (Exception e) {
            log.warn("星光余额查询降级（Fail-Open）: userId={}", userId, e);
            return null;
        }
    }
}
