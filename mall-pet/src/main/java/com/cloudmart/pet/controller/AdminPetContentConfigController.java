package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
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

    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkinConfigMapper skinConfigMapper;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetEvolutionConfigMapper evolutionConfigMapper;
    private final PetEventConfigMapper eventConfigMapper;

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
        return ApiResponse.ok(null);
    }

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

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static int orOne(Integer value) {
        return value != null ? value : 1;
    }
}
