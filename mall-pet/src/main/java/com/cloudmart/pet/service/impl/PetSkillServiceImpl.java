package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.LearnSkillRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.enums.PetSkillEffect;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetSkillService;
import com.cloudmart.pet.vo.PetSkillVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 宠物技能实现。
 *
 * <p>技能书不消耗：学会后仍留在背包作为收藏（前端以 {@code used=true} 灰显），
 * 避免"学会即消失"导致商城误判为可重复购买。</p>
 */
@Service
@Slf4j
public class PetSkillServiceImpl implements PetSkillService {

    private final PetService petService;
    private final PetItemCatalog itemCatalog;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetSkillMapper skillMapper;
    private final PetInventoryMapper inventoryMapper;

    public PetSkillServiceImpl(PetService petService,
                               PetItemCatalog itemCatalog,
                               PetSkillConfigMapper skillConfigMapper,
                               PetSkillMapper skillMapper,
                               PetInventoryMapper inventoryMapper) {
        this.petService = petService;
        this.itemCatalog = itemCatalog;
        this.skillConfigMapper = skillConfigMapper;
        this.skillMapper = skillMapper;
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    public List<PetSkillVO> skills(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        Set<String> learned = learnedCodes(pet.getId());
        Set<String> books = bookCodes(pet.getId());
        return skillConfigMapper.selectList(new LambdaQueryWrapper<PetSkillConfig>()
                        .eq(PetSkillConfig::getEnabled, true)
                        .orderByAsc(PetSkillConfig::getSort))
                .stream()
                .map(config -> toVo(pet, config, learned.contains(config.getCode()), books.contains(config.getCode())))
                .toList();
    }

    @Override
    public PetSkillVO learn(Long userId, LearnSkillRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetSkillConfig config = itemCatalog.skill(request.skillCode())
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_SKILL_NOT_FOUND,
                        "这个技能不存在或已下架"));
        Set<String> learned = learnedCodes(pet.getId());
        if (learned.contains(config.getCode())) {
            throw new BusinessException(PetErrorCodes.PET_SKILL_ALREADY_LEARNED, "这个技能已经学会啦");
        }
        if (!bookCodes(pet.getId()).contains(config.getCode())) {
            throw new BusinessException(PetErrorCodes.PET_SKILL_BOOK_REQUIRED,
                    "背包里还没有这本技能书，先去商城购买吧");
        }
        int level = config.getRequiredLevel() != null ? config.getRequiredLevel() : 1;
        if (pet.getLevel() < level) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED, "等级达到 Lv." + level + " 才能学习哦");
        }

        PetSkill skill = new PetSkill();
        skill.setPetId(pet.getId());
        skill.setUserId(userId);
        skill.setSkillCode(config.getCode());
        skill.setEquipped(true);
        skill.setLearnedAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            skillMapper.insert(skill);
        } catch (DuplicateKeyException e) {
            // 并发学习：uk_pet_skill 兜底
            throw new BusinessException(PetErrorCodes.PET_SKILL_ALREADY_LEARNED, "这个技能已经学会啦");
        }
        return toVo(pet, config, true, true);
    }

    private PetSkillVO toVo(Pet pet, PetSkillConfig config, boolean learned, boolean bookOwned) {
        int level = config.getRequiredLevel() != null ? config.getRequiredLevel() : 1;
        boolean levelOk = pet.getLevel() >= level;
        boolean eligible = learned || (bookOwned && levelOk);
        String lockReason = learned ? null
                : (!levelOk ? "需要 Lv." + level : (bookOwned ? null : "需要先购买技能书"));
        return new PetSkillVO(config.getCode(), config.getName(), config.getDescription(),
                config.getSkillType(), config.getEffect(), config.getEffectValue(),
                effectText(config.getEffect(), config.getEffectValue()),
                config.getIcon(), config.getPriceStarlight(), config.getRequiredLevel(),
                learned, learned, bookOwned, eligible, lockReason);
    }

    /** 效果文案由服务端生成（三端直出，避免各自拼百分比/点数导致口径不一致） */
    static String effectText(String effect, BigDecimal value) {
        double raw = value != null ? value.doubleValue() : 0;
        PetSkillEffect parsed;
        try {
            parsed = PetSkillEffect.valueOf(effect);
        } catch (IllegalArgumentException e) {
            return effect;
        }
        return switch (parsed) {
            case POWER_STRIKE -> "首回合伤害 +" + percent(raw);
            case LUCKY_FISH -> "捞瓶成功率 +" + percent(raw);
            case QUICK_STEP -> "战斗先手敏捷 +" + (int) Math.round(raw);
            case BOOKWORM -> "读书经验 +" + percent(raw);
            case CHARM_AURA -> "暴击率 +" + percent(raw);
            case TOUGH_BODY -> "受伤减免 " + percent(raw);
        };
    }

    private static String percent(double raw) {
        return Math.round(raw * 100) + "%";
    }

    private Set<String> learnedCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        skillMapper.selectList(new LambdaQueryWrapper<PetSkill>()
                        .eq(PetSkill::getPetId, petId))
                .forEach(skill -> codes.add(skill.getSkillCode()));
        return codes;
    }

    private Set<String> bookCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, petId)
                        .eq(PetInventory::getItemType, PetItemType.SKILL_BOOK.name()))
                .forEach(item -> codes.add(item.getItemCode()));
        return codes;
    }
}
