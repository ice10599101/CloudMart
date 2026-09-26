package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.enums.PetSkillEffect;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宠物属性结算领域服务（原文档 §89 装备/技能 → 玩法联动）。
 *
 * <p>宠物"基础属性（pet 表）+ 装备加成 + 技能被动"统一在此汇总，
 * 战斗快照、捞瓶成功率、读书收益都只读这里的结果——避免加成逻辑散落在各业务里
 * （任何一处漏算都会让装备/技能变成"只加面板不生效"）。</p>
 *
 * <p>纯读、无副作用；查询次数固定（装备 2 次 + 技能 2 次），与装备/技能数量无关。</p>
 */
@Component
public class PetStatsService {

    /** 受伤减免封顶（避免技能叠加后战斗无解） */
    private static final double MAX_DAMAGE_REDUCTION = 0.5;

    private final PetInventoryMapper inventoryMapper;
    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkillMapper skillMapper;
    private final PetSkillConfigMapper skillConfigMapper;

    public PetStatsService(PetInventoryMapper inventoryMapper,
                           PetEquipmentConfigMapper equipmentConfigMapper,
                           PetSkillMapper skillMapper,
                           PetSkillConfigMapper skillConfigMapper) {
        this.inventoryMapper = inventoryMapper;
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skillMapper = skillMapper;
        this.skillConfigMapper = skillConfigMapper;
    }

    /** 战斗属性快照（装备加成已并入基础属性，技能效果单列由引擎消费） */
    public record CombatStats(int hp, int maxHp, int strength, int intelligence, int agility, int charm,
                              double critBonus, double dodgeBonus, int damageBonus,
                              double powerStrikeBonus, double damageReduction) {
    }

    /** 汇总装备 + 技能后的战斗属性（等级 1 的宠物也保持 hp ≥ 1） */
    /** 基础属性（无装备/无技能被动，B12 预览用） */
    public CombatStats baseStats(Pet pet) {
        return new CombatStats(pet.getHp(), pet.getMaxHp(), pet.getStrength(),
                pet.getIntelligence(), pet.getAgility(), pet.getCharm(), 0, 0, 0, 0, 0);
    }

    /**
     * 试穿复算（B12 预览）：after = 当前总属性 - 该槽位现装备加成 + 目标装备加成；不写库。
     */
    public CombatStats combatStatsWithOverride(Pet pet, String slot, String equipmentCode) {
        CombatStats current = combatStats(pet);
        EquipBonus replaced = bonusOfEquippedInSlot(pet.getId(), slot);
        PetEquipmentConfig incoming = equipmentConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetEquipmentConfig>()
                        .eq(PetEquipmentConfig::getCode, equipmentCode)
                        .last("LIMIT 1"));
        if (incoming == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_ITEM_NOT_FOUND, "这件装备不存在");
        }
        EquipBonus add = new EquipBonus(orZero(incoming.getBonusStrength()), orZero(incoming.getBonusIntelligence()),
                orZero(incoming.getBonusAgility()), orZero(incoming.getBonusCharm()), orZero(incoming.getBonusMaxHp()));
        return new CombatStats(
                Math.min(current.hp() + add.maxHp() - replaced.maxHp(), current.maxHp() + add.maxHp() - replaced.maxHp()),
                current.maxHp() + add.maxHp() - replaced.maxHp(),
                current.strength() + add.strength() - replaced.strength(),
                current.intelligence() + add.intelligence() - replaced.intelligence(),
                current.agility() + add.agility() - replaced.agility(),
                current.charm() + add.charm() - replaced.charm(),
                current.critBonus(), current.dodgeBonus(), current.damageBonus(),
                current.powerStrikeBonus(), current.damageReduction());
    }

    private EquipBonus bonusOfEquippedInSlot(Long petId, String slot) {
        PetEquipmentConfig config = equippedEquipment(petId).get(slot);
        if (config == null) {
            return new EquipBonus(0, 0, 0, 0, 0);
        }
        return new EquipBonus(orZero(config.getBonusStrength()), orZero(config.getBonusIntelligence()),
                orZero(config.getBonusAgility()), orZero(config.getBonusCharm()), orZero(config.getBonusMaxHp()));
    }

    public CombatStats combatStats(Pet pet) {
        EquipBonus bonus = equipBonus(pet.getId());
        int maxHp = Math.max(1, pet.getMaxHp() + bonus.maxHp());
        int hp = Math.min(Math.max(0, pet.getHp()) + bonus.maxHp(), maxHp);
        return new CombatStats(
                hp, maxHp,
                pet.getStrength() + bonus.strength(),
                pet.getIntelligence() + bonus.intelligence(),
                pet.getAgility() + bonus.agility(),
                pet.getCharm() + bonus.charm(),
                skillValue(pet.getId(), PetSkillEffect.CHARM_AURA),
                0, // 闪避率当前由敏捷推导（PetBattleEngine），保留字段供后续装备/技能扩展
                0,
                skillValue(pet.getId(), PetSkillEffect.POWER_STRIKE),
                Math.min(MAX_DAMAGE_REDUCTION, skillValue(pet.getId(), PetSkillEffect.TOUGH_BODY)));
    }

    /** 捞瓶成功率加成（装备敏捷已并入 combatStats，这里只算技能被动） */
    public double bottleSuccessBonus(Pet pet) {
        return skillValue(pet.getId(), PetSkillEffect.LUCKY_FISH);
    }

    /** 读书经验加成比例（技能被动：博览群书） */
    public double studyExpBonus(Pet pet) {
        return skillValue(pet.getId(), PetSkillEffect.BOOKWORM);
    }

    /** 战斗先手敏捷加成点数（技能被动：迅捷身法，0 表示无该技能） */
    public int firstStrikeBonus(Pet pet) {
        return (int) Math.round(skillValue(pet.getId(), PetSkillEffect.QUICK_STEP));
    }

    /** 已装备的装备（slot → 配置），同一部位至多一件由写入路径保证 */
    public Map<String, PetEquipmentConfig> equippedEquipment(Long petId) {
        List<PetInventory> equipped = inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, petId)
                .eq(PetInventory::getItemType, PetItemType.EQUIPMENT.name())
                .eq(PetInventory::getEquipped, true));
        if (equipped.isEmpty()) {
            return Map.of();
        }
        List<String> codes = equipped.stream().map(PetInventory::getItemCode).toList();
        Map<String, PetEquipmentConfig> configs = new HashMap<>();
        equipmentConfigMapper.selectList(new LambdaQueryWrapper<PetEquipmentConfig>()
                        .in(PetEquipmentConfig::getCode, codes))
                .forEach(config -> configs.put(config.getCode(), config));
        Map<String, PetEquipmentConfig> result = new HashMap<>();
        for (PetInventory item : equipped) {
            PetEquipmentConfig config = configs.get(item.getItemCode());
            if (config != null) {
                result.put(config.getSlot(), config);
            }
        }
        return result;
    }

    /** 已学且已装配的技能配置（按装配顺序稳定返回） */
    public List<PetSkillConfig> equippedSkills(Long petId) {
        List<PetSkill> skills = skillMapper.selectList(new LambdaQueryWrapper<PetSkill>()
                .eq(PetSkill::getPetId, petId)
                .eq(PetSkill::getEquipped, true));
        if (skills.isEmpty()) {
            return List.of();
        }
        List<String> codes = skills.stream().map(PetSkill::getSkillCode).toList();
        Map<String, PetSkillConfig> configs = new HashMap<>();
        skillConfigMapper.selectList(new LambdaQueryWrapper<PetSkillConfig>()
                        .in(PetSkillConfig::getCode, codes))
                .forEach(config -> configs.put(config.getCode(), config));
        List<PetSkillConfig> result = new ArrayList<>();
        for (String code : codes) {
            PetSkillConfig config = configs.get(code);
            if (config != null) {
                result.add(config);
            }
        }
        return result;
    }

    /** 指定效果的效果值合计（同一效果多技能叠加；无则 0） */
    public double skillValue(Long petId, PetSkillEffect effect) {
        List<PetSkillConfig> skills = equippedSkills(petId);
        double value = 0;
        for (PetSkillConfig skill : skills) {
            if (effect.name().equals(skill.getEffect())) {
                BigDecimal effectValue = skill.getEffectValue();
                value += effectValue != null ? effectValue.doubleValue() : 0;
            }
        }
        return value;
    }

    private EquipBonus equipBonus(Long petId) {
        Map<String, PetEquipmentConfig> equipped = equippedEquipment(petId);
        int strength = 0;
        int intelligence = 0;
        int agility = 0;
        int charm = 0;
        int maxHp = 0;
        for (PetEquipmentConfig config : equipped.values()) {
            strength += orZero(config.getBonusStrength());
            intelligence += orZero(config.getBonusIntelligence());
            agility += orZero(config.getBonusAgility());
            charm += orZero(config.getBonusCharm());
            maxHp += orZero(config.getBonusMaxHp());
        }
        return new EquipBonus(strength, intelligence, agility, charm, maxHp);
    }

    private int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private record EquipBonus(int strength, int intelligence, int agility, int charm, int maxHp) {
    }
}
