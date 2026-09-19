package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.entity.PetCareerConfig;
import com.cloudmart.pet.entity.PetDailyQuestConfig;
import com.cloudmart.pet.entity.PetFurnitureConfig;
import com.cloudmart.pet.repository.PetCareerConfigMapper;
import com.cloudmart.pet.repository.PetDailyQuestConfigMapper;
import com.cloudmart.pet.repository.PetFurnitureConfigMapper;
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

import java.util.List;

/**
 * 宠物三期内容配置（职业/家具/每日任务）管理端接口。
 *
 * <p>与既有 {@code AdminPetConfigController}（岗位/课程）、{@code AdminPetContentConfigController}
 * （装备/皮肤/技能/进化/活动）同一套模式：<b>带 id 为更新，不带 id 为新增</b>；
 * 数值字段服务端权威，后台只负责录入。由 mall-admin 代理转发（{@code /admin/pet/**}）。</p>
 */
@RestController
@RequestMapping("/admin/pet")
@Tag(name = "宠物三期配置", description = "职业/家具/每日任务配置管理")
@RequiredArgsConstructor
public class AdminPetPhase3ConfigController {

    private final PetCareerConfigMapper careerConfigMapper;
    private final PetFurnitureConfigMapper furnitureConfigMapper;
    private final PetDailyQuestConfigMapper dailyQuestConfigMapper;

    // ---------------- 职业 ----------------

    @GetMapping("/careers")
    @Operation(summary = "职业列表", description = "全量（含停招）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetCareerConfig>> listCareers() {
        return ApiResponse.ok(careerConfigMapper.selectList(new LambdaQueryWrapper<PetCareerConfig>()
                .orderByAsc(PetCareerConfig::getCareerLine)
                .orderByAsc(PetCareerConfig::getTier)));
    }

    @PostMapping("/careers")
    @Operation(summary = "新增/更新职业", description = "promoteToCode 需指向同路线下一阶；带 id 为更新")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetCareerConfig> upsertCareer(@Valid @RequestBody CareerUpsertRequest request) {
        PetCareerConfig config = new PetCareerConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setCareerLine(request.careerLine());
        config.setTier(request.tier() != null ? request.tier() : 1);
        config.setIcon(request.icon() != null ? request.icon() : "💼");
        config.setRequiredLevel(orOne(request.requiredLevel()));
        config.setRequiredIntelligence(orZero(request.requiredIntelligence()));
        config.setDurationSeconds(request.durationSeconds());
        config.setEnergyCost(orZero(request.energyCost()));
        config.setHungerCost(orZero(request.hungerCost()));
        config.setExpReward(orZero(request.expReward()));
        config.setCurrencyReward(orZero(request.currencyReward()));
        config.setPromoteToCode(request.promoteToCode());
        config.setPromoteRequiredCount(orZero(request.promoteRequiredCount()));
        config.setPromoteStarCost(orZero(request.promoteStarCost()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            careerConfigMapper.updateById(config);
        } else {
            config.setId(null);
            careerConfigMapper.insert(config);
        }
        return ApiResponse.ok(config);
    }

    @PutMapping("/careers/{id}/enabled")
    @Operation(summary = "职业开放/停止招聘")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleCareer(@PathVariable("id") Long id,
                                          @RequestParam("enabled") Boolean enabled) {
        PetCareerConfig patch = new PetCareerConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        careerConfigMapper.updateById(patch);
        return ApiResponse.ok(null);
    }

    // ---------------- 家具 ----------------

    @GetMapping("/furniture")
    @Operation(summary = "家具列表", description = "全量（含下架）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetFurnitureConfig>> listFurniture() {
        return ApiResponse.ok(furnitureConfigMapper.selectList(new LambdaQueryWrapper<PetFurnitureConfig>()
                .orderByAsc(PetFurnitureConfig::getCategory)
                .orderByAsc(PetFurnitureConfig::getSort)));
    }

    @PostMapping("/furniture")
    @Operation(summary = "新增/更新家具", description = "category 必须是已实现枚举（WALL/FLOOR/...）；comfort 决定舒适度")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetFurnitureConfig> upsertFurniture(@Valid @RequestBody FurnitureUpsertRequest request) {
        PetFurnitureConfig config = new PetFurnitureConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setCategory(request.category());
        config.setIcon(request.icon() != null ? request.icon() : "🧸");
        config.setRarity(request.rarity() != null ? request.rarity() : "COMMON");
        config.setPriceStarlight(orZero(request.priceStarlight()));
        config.setRequiredLevel(orOne(request.requiredLevel()));
        config.setComfort(orZero(request.comfort()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            furnitureConfigMapper.updateById(config);
        } else {
            config.setId(null);
            furnitureConfigMapper.insert(config);
        }
        return ApiResponse.ok(config);
    }

    @PutMapping("/furniture/{id}/enabled")
    @Operation(summary = "家具上下架")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleFurniture(@PathVariable("id") Long id,
                                             @RequestParam("enabled") Boolean enabled) {
        PetFurnitureConfig patch = new PetFurnitureConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        furnitureConfigMapper.updateById(patch);
        return ApiResponse.ok(null);
    }

    // ---------------- 每日任务 ----------------

    @GetMapping("/daily-quests")
    @Operation(summary = "每日任务列表", description = "全量（含停用）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetDailyQuestConfig>> listDailyQuests() {
        return ApiResponse.ok(dailyQuestConfigMapper.selectList(new LambdaQueryWrapper<PetDailyQuestConfig>()
                .orderByAsc(PetDailyQuestConfig::getSort)));
    }

    @PostMapping("/daily-quests")
    @Operation(summary = "新增/更新每日任务", description = "questType 必须选已埋点口径，否则任务永远无法完成")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetDailyQuestConfig> upsertDailyQuest(@Valid @RequestBody DailyQuestUpsertRequest request) {
        PetDailyQuestConfig config = new PetDailyQuestConfig();
        config.setId(request.id());
        config.setCode(request.code());
        config.setName(request.name());
        config.setDescription(orEmpty(request.description()));
        config.setIcon(request.icon() != null ? request.icon() : "📌");
        config.setQuestType(request.questType());
        config.setTargetValue(request.targetValue());
        config.setExpReward(orZero(request.expReward()));
        config.setCurrencyReward(orZero(request.currencyReward()));
        config.setRequiredLevel(orOne(request.requiredLevel()));
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setSort(orZero(request.sort()));
        if (request.id() != null) {
            dailyQuestConfigMapper.updateById(config);
        } else {
            config.setId(null);
            dailyQuestConfigMapper.insert(config);
        }
        return ApiResponse.ok(config);
    }

    @PutMapping("/daily-quests/{id}/enabled")
    @Operation(summary = "每日任务启停", description = "停用后不再为当日新生成；已生成的任务仍可领取")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleDailyQuest(@PathVariable("id") Long id,
                                              @RequestParam("enabled") Boolean enabled) {
        PetDailyQuestConfig patch = new PetDailyQuestConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        dailyQuestConfigMapper.updateById(patch);
        return ApiResponse.ok(null);
    }

    // ---------------- 请求体 ----------------

    /** 职业配置请求 */
    public record CareerUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String careerLine,
            @Min(1) Integer tier,
            String icon,
            @Min(1) Integer requiredLevel,
            @Min(0) Integer requiredIntelligence,
            @NotNull @Min(60) Integer durationSeconds,
            @Min(0) Integer energyCost,
            @Min(0) Integer hungerCost,
            @Min(0) Integer expReward,
            @Min(0) Integer currencyReward,
            String promoteToCode,
            @Min(0) Integer promoteRequiredCount,
            @Min(0) Integer promoteStarCost,
            Boolean enabled,
            @Min(0) Integer sort) {
    }

    /** 家具配置请求 */
    public record FurnitureUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String category,
            String icon,
            String rarity,
            @Min(0) Integer priceStarlight,
            @Min(1) Integer requiredLevel,
            @Min(0) Integer comfort,
            Boolean enabled,
            @Min(0) Integer sort) {
    }

    /** 每日任务配置请求 */
    public record DailyQuestUpsertRequest(
            Long id,
            @NotBlank String code,
            @NotBlank String name,
            String description,
            String icon,
            @NotBlank String questType,
            @NotNull @Min(1) Integer targetValue,
            @Min(0) Integer expReward,
            @Min(0) Integer currencyReward,
            @Min(1) Integer requiredLevel,
            Boolean enabled,
            @Min(0) Integer sort) {
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
