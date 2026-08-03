package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.community.dto.CreateLevelConfigRequest;
import com.cloudmart.community.dto.UpdateLevelConfigRequest;
import com.cloudmart.community.service.AdminGrowthService;
import com.cloudmart.community.vo.LevelConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/growth")
@Tag(name = "成长体系管理(后台)", description = "管理后台等级配置与成长数据统计接口")
@RequiredArgsConstructor
public class AdminGrowthController {

    private final AdminGrowthService adminGrowthService;

    @GetMapping("/level-configs")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "等级配置列表", description = "管理后台分页查询等级配置列表")
    public ApiResponse<List<LevelConfigVO>> listLevelConfigs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<LevelConfigVO> result = adminGrowthService.listLevelConfigs(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PostMapping("/level-configs")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "创建等级配置", description = "管理后台创建新等级配置")
    public ApiResponse<LevelConfigVO> createLevelConfig(
            @Parameter(description = "创建等级配置请求") @Valid @RequestBody CreateLevelConfigRequest request) {
        LevelConfigVO vo = adminGrowthService.createLevelConfig(request);
        return ApiResponse.ok(vo);
    }

    @PutMapping("/level-configs/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新等级配置", description = "管理后台更新等级配置信息")
    public ApiResponse<LevelConfigVO> updateLevelConfig(
            @Parameter(description = "等级配置ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "更新等级配置请求") @Valid @RequestBody UpdateLevelConfigRequest request) {
        LevelConfigVO vo = adminGrowthService.updateLevelConfig(id, request);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/level-configs/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除等级配置", description = "管理后台删除等级配置")
    public ApiResponse<Void> deleteLevelConfig(
            @Parameter(description = "等级配置ID", required = true) @PathVariable("id") Long id) {
        adminGrowthService.deleteLevelConfig(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/level-configs/{id}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "切换等级配置状态", description = "管理后台切换等级配置启用/禁用状态")
    public ApiResponse<Void> updateLevelConfigStatus(
            @Parameter(description = "等级配置ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "状态值") @RequestParam Integer status) {
        adminGrowthService.updateLevelConfigStatus(id, status);
        return ApiResponse.ok(null);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "成长体系统计", description = "获取签到统计数据")
    public ApiResponse<Map<String, Long>> getStats() {
        long totalCheckIns = adminGrowthService.getTotalCheckIns();
        long todayCheckIns = adminGrowthService.getTodayCheckIns();
        return ApiResponse.ok(Map.of("totalCheckIns", totalCheckIns, "todayCheckIns", todayCheckIns));
    }
}
