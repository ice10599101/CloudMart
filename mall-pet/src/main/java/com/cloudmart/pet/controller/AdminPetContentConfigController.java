package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetEventConfig;
import com.cloudmart.pet.entity.PetEvolutionConfig;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetEventConfigMapper;
import com.cloudmart.pet.repository.PetEvolutionConfigMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 宠物二期内容配置管理端（装备/皮肤/技能/进化/活动）。
 *
 * <p>与 {@link AdminPetConfigController} 同一安全模型：mall-admin Feign 代理访问，
 * {@code hasRole('INTERNAL')}。数值一律后台维护——前端与 Cocos 不得硬编码价格/加成
 * （原文档 §11 配置化要求，二期扩展内容同样适用）。</p>
 */
@RestController
@RequestMapping("/admin/configs")
@Tag(name = "宠物·管理端内容配置", description = "装备/皮肤/技能/进化/活动配置维护（内部调用）")
@RequiredArgsConstructor
public class AdminPetContentConfigController {

    private final com.cloudmart.pet.service.impl.PetConfigGovernanceService governance;
    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkinConfigMapper skinConfigMapper;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetEvolutionConfigMapper evolutionConfigMapper;
    private final PetEventConfigMapper eventConfigMapper;
    private final com.cloudmart.pet.repository.PetEventOccurrenceMapper occurrenceMapper;
    private final com.cloudmart.pet.repository.PetContentSensitiveWordMapper sensitiveWordMapper;
    private final com.cloudmart.pet.service.impl.PetContentSafetyService contentSafetyService;
    private final com.cloudmart.pet.repository.PetFoodConfigMapper foodConfigMapper;
    private final com.cloudmart.pet.repository.PetPersonaPhraseMapper personaPhraseMapper;
    private final com.cloudmart.pet.service.impl.PetItemCatalog itemCatalog;

    // ---------------- 装备 ----------------

    @GetMapping("/equipment")
    @Operation(summary = "装备列表", description = "全量（含下架）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetEquipmentConfig>> listEquipment() {
        return ApiResponse.ok(equipmentConfigMapper.selectList(new LambdaQueryWrapper<PetEquipmentConfig>()
                .orderByAsc(PetEquipmentConfig::getSort)));
    }

    @PostMapping("/equipment")
    @Operation(summary = "新增/更新装备", description = "带 id 为更新；价格/加成为服务端权威")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetEquipmentConfig> upsertEquipment(@Valid @RequestBody EquipmentUpsertRequest request) {
        governance.validateDto("equipment", request);
        PetEquipmentConfig config = new PetEquipmentConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setSlot(request.slot());
        config.setIcon(request.icon() != null ? request.icon() : "🎀");
        config.setRarity(request.rarity() != null ? request.rarity() : "COMMON");
        config.setPriceStarlight(request.priceStarlight());
        config.setBonusStrength(orZero(request.bonusStrength()));
        config.setBonusIntelligence(orZero(request.bonusIntelligence()));
        config.setBonusAgility(orZero(request.bonusAgility()));
        config.setBonusCharm(orZero(request.bonusCharm()));
        config.setBonusMaxHp(orZero(request.bonusMaxHp()));
        config.setRequiredLevel(orOne(request.requiredLevel()));
        config.setRequiredEvolutionStage(orZero(request.requiredEvolutionStage()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            equipmentConfigMapper.updateById(config);
        } else {
            config.setId(null);
            equipmentConfigMapper.insert(config);
        }
        // ADM-03：写后快照 + 版本登记（写前校验见下方 validateDto），与写库同事务
        governance.snapshotAndRecord("equipment", config.getId(),
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(config);
    }

    @PutMapping("/equipment/{id}/enabled")
    @Operation(summary = "装备上下架")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleEquipment(@PathVariable("id") Long id,
                                             @RequestParam("enabled") Boolean enabled) {
        PetEquipmentConfig patch = new PetEquipmentConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        equipmentConfigMapper.updateById(patch);
        governance.snapshotAndRecord("equipment", id,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(null);
    }

    // ---------------- 皮肤 ----------------

    @GetMapping("/skins")
    @Operation(summary = "皮肤列表", description = "全量（含下架）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetSkinConfig>> listSkins() {
        return ApiResponse.ok(skinConfigMapper.selectList(new LambdaQueryWrapper<PetSkinConfig>()
                .orderByAsc(PetSkinConfig::getSort)));
    }

    @PostMapping("/skins")
    @Operation(summary = "新增/更新皮肤", description = "species 为空表示通用皮肤")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetSkinConfig> upsertSkin(@Valid @RequestBody SkinUpsertRequest request) {
        governance.validateDto("skin", request);
        PetSkinConfig config = new PetSkinConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setSpecies(request.species());
        config.setColor(request.color());
        config.setAccessory(request.accessory() != null ? request.accessory() : "none");
        config.setIcon(request.icon() != null ? request.icon() : "✨");
        config.setRarity(request.rarity() != null ? request.rarity() : "COMMON");
        config.setPriceStarlight(orZero(request.priceStarlight()));
        config.setRequiredLevel(orOne(request.requiredLevel()));
        config.setRequiredEvolutionStage(orZero(request.requiredEvolutionStage()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            skinConfigMapper.updateById(config);
        } else {
            config.setId(null);
            skinConfigMapper.insert(config);
        }
        // ADM-03：写后快照 + 版本登记（写前校验见下方 validateDto），与写库同事务
        governance.snapshotAndRecord("skin", config.getId(),
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(config);
    }

    @PutMapping("/skins/{id}/enabled")
    @Operation(summary = "皮肤上下架")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleSkin(@PathVariable("id") Long id,
                                        @RequestParam("enabled") Boolean enabled) {
        PetSkinConfig patch = new PetSkinConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        skinConfigMapper.updateById(patch);
        governance.snapshotAndRecord("skin", id,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(null);
    }

    // ---------------- 技能 ----------------

    @GetMapping("/skills")
    @Operation(summary = "技能列表", description = "全量（含下架）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetSkillConfig>> listSkills() {
        return ApiResponse.ok(skillConfigMapper.selectList(new LambdaQueryWrapper<PetSkillConfig>()
                .orderByAsc(PetSkillConfig::getSort)));
    }

    @PostMapping("/skills")
    @Operation(summary = "新增/更新技能", description = "effect 必须是服务端已实现的枚举值，否则不生效")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetSkillConfig> upsertSkill(@Valid @RequestBody SkillUpsertRequest request) {
        governance.validateDto("skill", request);
        PetSkillConfig config = new PetSkillConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setSkillType(request.skillType());
        config.setEffect(request.effect());
        config.setEffectValue(request.effectValue() != null ? request.effectValue() : BigDecimal.ZERO);
        config.setIcon(request.icon() != null ? request.icon() : "🌟");
        config.setPriceStarlight(orZero(request.priceStarlight()));
        config.setRequiredLevel(orOne(request.requiredLevel()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            skillConfigMapper.updateById(config);
        } else {
            config.setId(null);
            skillConfigMapper.insert(config);
        }
        // ADM-03：写后快照 + 版本登记（写前校验见下方 validateDto），与写库同事务
        governance.snapshotAndRecord("skill", config.getId(),
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(config);
    }

    @PutMapping("/skills/{id}/enabled")
    @Operation(summary = "技能上下架")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleSkill(@PathVariable("id") Long id,
                                         @RequestParam("enabled") Boolean enabled) {
        PetSkillConfig patch = new PetSkillConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        skillConfigMapper.updateById(patch);
        governance.snapshotAndRecord("skill", id,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(null);
    }

    // ---------------- 进化 ----------------

    @GetMapping("/evolutions")
    @Operation(summary = "进化链列表", description = "全量（含停用）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetEvolutionConfig>> listEvolutions() {
        return ApiResponse.ok(evolutionConfigMapper.selectList(new LambdaQueryWrapper<PetEvolutionConfig>()
                .orderByAsc(PetEvolutionConfig::getSort)));
    }

    @PostMapping("/evolutions")
    @Operation(summary = "新增/更新进化", description = "stageFrom → stageTo 必须逐阶衔接，否则该阶段无法进化")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetEvolutionConfig> upsertEvolution(@Valid @RequestBody EvolutionUpsertRequest request) {
        governance.validateDto("evolution", request);
        PetEvolutionConfig config = new PetEvolutionConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setStageFrom(request.stageFrom());
        config.setStageTo(request.stageTo());
        config.setRequiredLevel(request.requiredLevel());
        config.setCostStarlight(orZero(request.costStarlight()));
        config.setBonusMaxHp(orZero(request.bonusMaxHp()));
        config.setBonusStrength(orZero(request.bonusStrength()));
        config.setBonusIntelligence(orZero(request.bonusIntelligence()));
        config.setBonusAgility(orZero(request.bonusAgility()));
        config.setBonusCharm(orZero(request.bonusCharm()));
        config.setUnlockSkinCode(request.unlockSkinCode());
        config.setIcon(request.icon() != null ? request.icon() : "🌠");
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            evolutionConfigMapper.updateById(config);
        } else {
            config.setId(null);
            evolutionConfigMapper.insert(config);
        }
        // ADM-03：写后快照 + 版本登记（写前校验见下方 validateDto），与写库同事务
        governance.snapshotAndRecord("evolution", config.getId(),
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(config);
    }

    @PutMapping("/evolutions/{id}/enabled")
    @Operation(summary = "进化启用/停用")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleEvolution(@PathVariable("id") Long id,
                                             @RequestParam("enabled") Boolean enabled) {
        PetEvolutionConfig patch = new PetEvolutionConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        evolutionConfigMapper.updateById(patch);
        governance.snapshotAndRecord("evolution", id,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(null);
    }

    // ---------------- 社区宠物活动 ----------------

    @GetMapping("/events")
    @Operation(summary = "活动列表", description = "全量（含下架）；startsAt/endsAt 为空表示常驻")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetEventConfig>> listEvents() {
        return ApiResponse.ok(eventConfigMapper.selectList(new LambdaQueryWrapper<PetEventConfig>()
                .orderByAsc(PetEventConfig::getSort)));
    }

    @PostMapping("/events")
    @Operation(summary = "新增/更新活动", description = "进度统计口径 eventType 必须是已实现枚举值")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetEventConfig> upsertEvent(@Valid @RequestBody EventUpsertRequest request) {
        governance.validateDto("event", request);
        PetEventConfig config = new PetEventConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setEventType(request.eventType());
        config.setTargetValue(request.targetValue());
        config.setRewardStarlight(orZero(request.rewardStarlight()));
        config.setRewardExp(orZero(request.rewardExp()));
        config.setRewardItemCode(request.rewardItemCode());
        config.setStartsAt(request.startsAt());
        config.setEndsAt(request.endsAt());
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            eventConfigMapper.updateById(config);
        } else {
            config.setId(null);
            eventConfigMapper.insert(config);
        }
        // ADM-03：写后快照 + 版本登记（写前校验见下方 validateDto），与写库同事务
        governance.snapshotAndRecord("event", config.getId(),
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(config);
    }

    @PutMapping("/events/{id}/enabled")
    @Operation(summary = "活动上下架")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleEvent(@PathVariable("id") Long id,
                                         @RequestParam("enabled") Boolean enabled) {
        PetEventConfig patch = new PetEventConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        eventConfigMapper.updateById(patch);
        governance.snapshotAndRecord("event", id,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(null);
    }

    // ---------------- 敏感词库（P0-1 内容安全） ----------------

    @GetMapping("/sensitive-words")
    @Operation(summary = "敏感词列表", description = "status 过滤 + 分页（内容安全词库）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<com.cloudmart.pet.entity.PetContentSensitiveWord>> listSensitiveWords(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        LambdaQueryWrapper<com.cloudmart.pet.entity.PetContentSensitiveWord> wrapper =
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetContentSensitiveWord>()
                        .orderByDesc(com.cloudmart.pet.entity.PetContentSensitiveWord::getId);
        if (status != null) {
            wrapper.eq(com.cloudmart.pet.entity.PetContentSensitiveWord::getStatus, status);
        }
        Page<com.cloudmart.pet.entity.PetContentSensitiveWord> result =
                sensitiveWordMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(size, 100)), wrapper);
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @PostMapping("/sensitive-words")
    @Operation(summary = "新增/更新敏感词", description = "带 id 为更新；category: POLITICS/ABUSE/AD/CRISIS")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<com.cloudmart.pet.entity.PetContentSensitiveWord> upsertSensitiveWord(
            @Valid @RequestBody SensitiveWordUpsertRequest request) {
        String normalizedWord = request.word().strip();
        if (normalizedWord.isEmpty() || normalizedWord.length() > 64) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "敏感词长度需 1~64 字");
        }
        if (!com.cloudmart.pet.service.impl.PetContentSafetyService.CATEGORIES.contains(request.category())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "类别必须是 " + com.cloudmart.pet.service.impl.PetContentSafetyService.CATEGORIES);
        }
        com.cloudmart.pet.entity.PetContentSensitiveWord entry = new com.cloudmart.pet.entity.PetContentSensitiveWord();
        entry.setId(request.id());
        entry.setWord(normalizedWord);
        entry.setCategory(request.category());
        entry.setStatus(request.enabled() == null || request.enabled() ? 1 : 0);
        try {
            if (request.id() != null && sensitiveWordMapper.selectById(request.id()) != null) {
                sensitiveWordMapper.updateById(entry);
            } else {
                entry.setId(null);
                sensitiveWordMapper.insert(entry);
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "该敏感词已存在");
        }
        governance.snapshotAndRecord("sensitive_word", entry.getId(),
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        contentSafetyService.refresh();
        return ApiResponse.ok(entry);
    }

    @DeleteMapping("/sensitive-words/{id}")
    @Operation(summary = "删除敏感词", description = "词库条目物理删除（非业务数据，重加同词不冲突）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> deleteSensitiveWord(@PathVariable("id") Long id) {
        if (sensitiveWordMapper.selectById(id) == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "敏感词不存在");
        }
        sensitiveWordMapper.deleteById(id);
        governance.snapshotAndRecord("sensitive_word", id,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        contentSafetyService.refresh();
        return ApiResponse.ok(null);
    }

    // ---------------- 食物配置（F1 配置化） ----------------

    @GetMapping("/foods")
    @Operation(summary = "食物列表", description = "全量（含下架）；喂养效果服务端权威")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<com.cloudmart.pet.entity.PetFoodConfig>> listFoods() {
        return ApiResponse.ok(foodConfigMapper.selectList(new LambdaQueryWrapper<com.cloudmart.pet.entity.PetFoodConfig>()
                .orderByAsc(com.cloudmart.pet.entity.PetFoodConfig::getSort)));
    }

    /** 食物配置请求（F1） */
    public record FoodUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String icon,
            String description,
            @NotNull @Min(0) Integer priceStarlight,
            @NotNull @Min(0) @jakarta.validation.constraints.Max(100) Integer hunger,
            @NotNull @Min(0) @jakarta.validation.constraints.Max(100) Integer happiness,
            @NotNull @Min(0) Integer hp,
            Boolean enabled,
            Integer sort
    ) {
    }

    @PostMapping("/foods")
    @Operation(summary = "新增/更新食物", description = "带 id 为更新；价格/效果服务端权威（0≤hunger/happiness≤100）；本实例即时生效，其他实例 ≤60s")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<com.cloudmart.pet.entity.PetFoodConfig> upsertFood(
            @Valid @RequestBody FoodUpsertRequest request) {
        com.cloudmart.pet.entity.PetFoodConfig config = new com.cloudmart.pet.entity.PetFoodConfig();
        config.setId(request.id());
        config.setCode(request.code().strip());
        config.setName(request.name().strip());
        config.setIcon(request.icon() != null && !request.icon().isBlank() ? request.icon() : "🍎");
        config.setDescription(request.description() != null ? request.description() : "");
        config.setPriceStarlight(request.priceStarlight());
        config.setHunger(request.hunger());
        config.setHappiness(request.happiness());
        config.setHp(request.hp());
        config.setEnabled(request.enabled() == null || request.enabled() ? 1 : 0);
        config.setSort(request.sort() != null ? request.sort() : 0);
        try {
            if (request.id() != null && foodConfigMapper.selectById(request.id()) != null) {
                foodConfigMapper.updateById(config);
            } else {
                config.setId(null);
                foodConfigMapper.insert(config);
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "食物编码已存在: " + config.getCode());
        }
        governance.snapshotAndRecord("food", config.getId(),
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        itemCatalog.invalidateFoodCache();
        return ApiResponse.ok(config);
    }

    @PutMapping("/foods/{id}/enabled")
    @Operation(summary = "食物上下架")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleFood(@PathVariable("id") Long id,
                                        @RequestParam("enabled") Boolean enabled) {
        com.cloudmart.pet.entity.PetFoodConfig patch = new com.cloudmart.pet.entity.PetFoodConfig();
        patch.setId(id);
        patch.setEnabled(enabled ? 1 : 0);
        foodConfigMapper.updateById(patch);
        governance.snapshotAndRecord("food", id,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        itemCatalog.invalidateFoodCache();
        return ApiResponse.ok(null);
    }

    // ---------------- 人设口头禅（F8 配置化） ----------------

    @GetMapping("/persona-phrases")
    @Operation(summary = "口头禅列表", description = "按性格一行；DB 无行的性格回落 Nacos 出厂默认（读取结果标 source）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<java.util.Map<String, String>>> listPersonaPhrases() {
        java.util.Map<String, com.cloudmart.pet.entity.PetPersonaPhrase> byDb = new java.util.HashMap<>();
        personaPhraseMapper.selectList(null)
                .forEach(row -> byDb.put(row.getPersonality(), row));
        List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        for (com.cloudmart.pet.enums.PetPersonality personality : com.cloudmart.pet.enums.PetPersonality.values()) {
            com.cloudmart.pet.entity.PetPersonaPhrase row = byDb.get(personality.name());
            java.util.Map<String, String> item = new java.util.LinkedHashMap<>();
            item.put("personality", personality.name());
            item.put("phrase", row != null ? row.getPhrase()
                    : com.cloudmart.pet.service.impl.PetPersonaPhraseService.PHRASE_PLACEHOLDER);
            item.put("source", row != null ? "DB" : "DEFAULT");
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    /** 口头禅保存请求（F8） */
    public record PersonaPhraseUpsertRequest(
            @NotBlank String personality,
            @NotBlank String phrase) {
    }

    @PostMapping("/persona-phrases")
    @Operation(summary = "保存口头禅", description = "按性格 upsert；{name} 占位宠物名；60 秒内同步到全部实例")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> upsertPersonaPhrase(@Valid @RequestBody PersonaPhraseUpsertRequest request) {
        try {
            com.cloudmart.pet.enums.PetPersonality.valueOf(request.personality());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "性格编码非法: " + request.personality());
        }
        String phrase = request.phrase().strip();
        if (phrase.isEmpty() || phrase.length() > 64) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "口头禅需 1~64 字");
        }
        personaPhraseService.save(request.personality(), phrase);
        governance.snapshotAndRecord("persona_phrase", 0L,
                com.cloudmart.pet.service.impl.PetConfigGovernanceService.currentOperator());
        return ApiResponse.ok(null);
    }

    private final com.cloudmart.pet.service.impl.PetPersonaPhraseService personaPhraseService;

    // ---------------- 请求体 ----------------

    /** 装备配置请求 */
    public record EquipmentUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String slot,
            String icon,
            String rarity,
            @NotNull @Min(0) Integer priceStarlight,
            @Min(0) Integer bonusStrength,
            @Min(0) Integer bonusIntelligence,
            @Min(0) Integer bonusAgility,
            @Min(0) Integer bonusCharm,
            @Min(0) Integer bonusMaxHp,
            @Min(1) Integer requiredLevel,
            @Min(0) Integer requiredEvolutionStage,
            Boolean enabled,
            Integer sort
    ) {
    }

    /** 皮肤配置请求 */
    public record SkinUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            String species,
            @NotBlank String color,
            String accessory,
            String icon,
            String rarity,
            @Min(0) Integer priceStarlight,
            @Min(1) Integer requiredLevel,
            @Min(0) Integer requiredEvolutionStage,
            Boolean enabled,
            Integer sort
    ) {
    }

    /** 技能配置请求 */
    public record SkillUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String skillType,
            @NotBlank String effect,
            BigDecimal effectValue,
            String icon,
            @Min(0) Integer priceStarlight,
            @Min(1) Integer requiredLevel,
            Boolean enabled,
            Integer sort
    ) {
    }

    /** 进化配置请求 */
    public record EvolutionUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotNull @Min(0) Integer stageFrom,
            @NotNull @Min(1) Integer stageTo,
            @NotNull @Min(1) Integer requiredLevel,
            @Min(0) Integer costStarlight,
            @Min(0) Integer bonusMaxHp,
            @Min(0) Integer bonusStrength,
            @Min(0) Integer bonusIntelligence,
            @Min(0) Integer bonusAgility,
            @Min(0) Integer bonusCharm,
            String unlockSkinCode,
            String icon,
            Boolean enabled,
            Integer sort
    ) {
    }

    /** 活动配置请求 */
    public record EventUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String eventType,
            @NotNull @Min(1) Integer targetValue,
            @Min(0) Integer rewardStarlight,
            @Min(0) Integer rewardExp,
            String rewardItemCode,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Boolean enabled,
            Integer sort
    ) {
    }

    /** 敏感词请求（P0-1 内容安全词库） */
    public record SensitiveWordUpsertRequest(
            Long id,
            @NotBlank String word,
            @NotBlank String category,
            Boolean enabled
    ) {
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static int orOne(Integer value) {
        return value != null ? value : 1;
    }

    // ---------------- 活动期次（R33：occurrence 发布/关闭） ----------------

    public record OccurrencePublishRequest(java.time.LocalDateTime startAt,
                                           java.time.LocalDateTime endAt,
                                           Integer graceHours) {
    }

    /** 期次领奖宽限默认 24h（与用户侧 PetEventServiceImpl.CLAIM_GRACE_HOURS 口径一致） */
    private static final int OCCURRENCE_CLAIM_GRACE_HOURS_DEFAULT = 24;

    @GetMapping("/events/{code}/occurrences")
    @Operation(summary = "活动期次列表（R33）", description = "按期号倒序；status=ACTIVE/CLOSED")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<com.cloudmart.pet.entity.PetEventOccurrence>> listOccurrence(
            @PathVariable("code") String code) {
        return ApiResponse.ok(occurrenceMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetEventOccurrence>()
                        .eq(com.cloudmart.pet.entity.PetEventOccurrence::getEventCode, code)
                        .orderByDesc(com.cloudmart.pet.entity.PetEventOccurrence::getOccurrenceIndex)));
    }

    @PostMapping("/events/{code}/occurrences")
    @Operation(summary = "发布活动期次（R33）", description = "occurrence_index=当前最大+1；奖励快照从当前配置冻结；"
            + "同一 code 同时至多一个进行中期次（领奖截止未过），避免旧 eventCode 入口歧义")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<com.cloudmart.pet.entity.PetEventOccurrence> publishOccurrence(
            @PathVariable("code") String code, @RequestBody OccurrencePublishRequest request) {
        PetEventConfig config = eventConfigMapper.selectOne(new LambdaQueryWrapper<PetEventConfig>()
                .eq(PetEventConfig::getCode, code).last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(PetErrorCodes.PET_EVENT_NOT_FOUND, "活动不存在");
        }
        if (request.startAt() == null || request.endAt() == null
                || !request.startAt().isBefore(request.endAt())) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "窗口时间非法（需 startAt < endAt）");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        com.cloudmart.pet.entity.PetEventOccurrence live = occurrenceMapper.selectOne(
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetEventOccurrence>()
                        .eq(com.cloudmart.pet.entity.PetEventOccurrence::getEventCode, code)
                        .eq(com.cloudmart.pet.entity.PetEventOccurrence::getStatus, "ACTIVE")
                        .gt(com.cloudmart.pet.entity.PetEventOccurrence::getClaimDeadlineAt, now)
                        .last("LIMIT 1"));
        if (live != null) {
            throw new BusinessException(PetErrorCodes.PET_ACTIVITY_CONFLICT,
                    "该活动已有进行中期次（领奖截止 " + live.getClaimDeadlineAt() + "），请先关闭或等其截止");
        }
        com.cloudmart.pet.entity.PetEventOccurrence last = occurrenceMapper.selectOne(
                new LambdaQueryWrapper<com.cloudmart.pet.entity.PetEventOccurrence>()
                        .eq(com.cloudmart.pet.entity.PetEventOccurrence::getEventCode, code)
                        .orderByDesc(com.cloudmart.pet.entity.PetEventOccurrence::getOccurrenceIndex)
                        .last("LIMIT 1"));
        int graceHours = request.graceHours() == null || request.graceHours() <= 0
                ? OCCURRENCE_CLAIM_GRACE_HOURS_DEFAULT : request.graceHours();
        com.cloudmart.pet.entity.PetEventOccurrence occurrence = new com.cloudmart.pet.entity.PetEventOccurrence();
        occurrence.setEventCode(code);
        occurrence.setOccurrenceIndex(last == null ? 1 : last.getOccurrenceIndex() + 1);
        occurrence.setStartAt(request.startAt());
        occurrence.setEndAt(request.endAt());
        occurrence.setClaimDeadlineAt(request.endAt().plusHours(graceHours));
        // 奖励快照：发布时冻结当前配置的奖励字段（后续改配置不影响本期）
        occurrence.setRewardSnapshot(com.cloudmart.pet.util.PetJsonUtils.toJson(java.util.Map.of(
                "targetValue", config.getTargetValue() == null ? 1 : config.getTargetValue(),
                "rewardStarlight", config.getRewardStarlight() == null ? 0 : config.getRewardStarlight(),
                "rewardExp", config.getRewardExp() == null ? 0 : config.getRewardExp(),
                "rewardItemCode", config.getRewardItemCode() == null ? "" : config.getRewardItemCode())));
        occurrence.setStatus("ACTIVE");
        occurrenceMapper.insert(occurrence);
        return ApiResponse.ok(occurrence);
    }

    @PostMapping("/event-occurrences/{id}/close")
    @Operation(summary = "关闭期次（R33）", description = "CAS ACTIVE→CLOSED；关闭后统计/发布入口不再解析该期，"
            + "已入期次领奖事实不变（期次入口到 claimDeadline 前仍可领）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> closeOccurrence(@PathVariable("id") Long id) {
        int updated = occurrenceMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.cloudmart.pet.entity.PetEventOccurrence>()
                        .set(com.cloudmart.pet.entity.PetEventOccurrence::getStatus, "CLOSED")
                        .eq(com.cloudmart.pet.entity.PetEventOccurrence::getId, id)
                        .eq(com.cloudmart.pet.entity.PetEventOccurrence::getStatus, "ACTIVE"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "期次不存在或已关闭");
        }
        return ApiResponse.ok(null);
    }

    // ---------------- 记忆管理（管理端） ----------------
}
