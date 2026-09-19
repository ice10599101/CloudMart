package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.entity.PetJobConfig;
import com.cloudmart.pet.entity.PetStudyConfig;
import com.cloudmart.pet.repository.PetJobConfigMapper;
import com.cloudmart.pet.repository.PetStudyConfigMapper;
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
 * 宠物配置管理端（mall-admin Feign 代理访问，{@code hasRole('INTERNAL')}）。
 * 打工/读书参数必须在后台配置，禁止硬编码在前端（原文档 §11）。
 */
@RestController
@RequestMapping("/admin/configs")
@Tag(name = "宠物·管理端配置", description = "打工岗位/读书课程配置维护（内部调用）")
@RequiredArgsConstructor
public class AdminPetConfigController {

    private final PetJobConfigMapper jobConfigMapper;
    private final PetStudyConfigMapper studyConfigMapper;

    @GetMapping("/jobs")
    @Operation(summary = "岗位列表", description = "全量（含停用）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetJobConfig>> listJobs() {
        return ApiResponse.ok(jobConfigMapper.selectList(new LambdaQueryWrapper<PetJobConfig>()
                .orderByAsc(PetJobConfig::getSort)));
    }

    @PostMapping("/jobs")
    @Operation(summary = "新增/更新岗位", description = "带 id 为更新；参数服务端权威")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetJobConfig> upsertJob(@Valid @RequestBody JobUpsertRequest request) {
        PetJobConfig config = new PetJobConfig();
        config.setId(request.id());
        config.setName(request.name());
        config.setDescription(request.description() != null ? request.description() : "");
        config.setDurationSeconds(request.durationSeconds());
        config.setEnergyCost(request.energyCost());
        config.setHungerCost(request.hungerCost());
        config.setExpReward(request.expReward());
        config.setCurrencyReward(request.currencyReward());
        config.setRequiredLevel(request.requiredLevel());
        config.setEnabled(request.enabled() != null ? request.enabled() : true);
        config.setSort(request.sort() != null ? request.sort() : 0);
        if (request.id() != null) {
            jobConfigMapper.updateById(config);
        } else {
            config.setId(null);
            jobConfigMapper.insert(config);
        }
        return ApiResponse.ok(config);
    }

    @PutMapping("/jobs/{id}/enabled")
    @Operation(summary = "岗位启停")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> toggleJob(@PathVariable("id") Long id,
                                       @RequestParam("enabled") Boolean enabled) {
        PetJobConfig patch = new PetJobConfig();
        patch.setId(id);
        patch.setEnabled(enabled);
        jobConfigMapper.updateById(patch);
        return ApiResponse.ok(null);
    }

    @GetMapping("/studies")
    @Operation(summary = "课程列表", description = "全量（含停用）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<PetStudyConfig>> listStudies() {
        return ApiResponse.ok(studyConfigMapper.selectList(new LambdaQueryWrapper<PetStudyConfig>()
                .orderByAsc(PetStudyConfig::getSort)));
    }

    @PostMapping("/studies")
    @Operation(summary = "新增/更新课程", description = "带 id 为更新")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetStudyConfig> upsertStudy(@Valid @RequestBody StudyUpsertRequest request) {
        PetStudyConfig config = new PetStudyConfig();
        config.setId(request.id());
        config.setName(request.name());
        config.setDescription(request.description() != null ? request.description() : "");
        config.setCategory(request.category() != null ? request.category() : "GENERAL");
        config.setDurationSeconds(request.durationSeconds());
        config.setEnergyCost(request.energyCost());
        config.setExpReward(request.expReward());
        config.setIntelligenceReward(request.intelligenceReward());
        config.setRequiredLevel(request.requiredLevel());
        config.setEnabled(request.enabled() != null ? request.enabled() : true);
        config.setSort(request.sort() != null ? request.sort() : 0);
        if (request.id() != null) {
            studyConfigMapper.updateById(config);
        } else {
            config.setId(null);
            studyConfigMapper.insert(config);
        }
        return ApiResponse.ok(config);
    }

    /** 岗位配置请求 */
    public record JobUpsertRequest(
            Long id,
            @NotBlank String name,
            String description,
            @NotNull @Min(60) Integer durationSeconds,
            @NotNull @Min(0) Integer energyCost,
            @NotNull @Min(0) Integer hungerCost,
            @NotNull @Min(0) Integer expReward,
            @NotNull @Min(0) Integer currencyReward,
            @NotNull @Min(1) Integer requiredLevel,
            Boolean enabled,
            Integer sort
    ) {
    }

    /** 课程配置请求 */
    public record StudyUpsertRequest(
            Long id,
            @NotBlank String name,
            String description,
            String category,
            @NotNull @Min(60) Integer durationSeconds,
            @NotNull @Min(0) Integer energyCost,
            @NotNull @Min(0) Integer expReward,
            @NotNull @Min(0) Integer intelligenceReward,
            @NotNull @Min(1) Integer requiredLevel,
            Boolean enabled,
            Integer sort
    ) {
    }
}
