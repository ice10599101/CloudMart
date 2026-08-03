package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.community.dto.CreateBadgeRequest;
import com.cloudmart.community.dto.UpdateBadgeRequest;
import com.cloudmart.community.service.BadgeService;
import com.cloudmart.community.vo.BadgeVO;
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
@RequestMapping("/admin/badges")
@Tag(name = "徽章管理(后台)", description = "管理后台徽章管理接口")
@RequiredArgsConstructor
public class AdminBadgeController {

    private final BadgeService badgeService;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "徽章列表", description = "管理后台分页查询徽章列表")
    public ApiResponse<List<BadgeVO>> listBadges(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<BadgeVO> result = badgeService.listBadges(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "创建徽章", description = "管理后台创建新徽章")
    public ApiResponse<BadgeVO> createBadge(
            @Parameter(description = "创建徽章请求") @Valid @RequestBody CreateBadgeRequest request) {
        BadgeVO vo = badgeService.createBadge(request);
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新徽章", description = "管理后台更新徽章信息")
    public ApiResponse<BadgeVO> updateBadge(
            @Parameter(description = "徽章ID", required = true) @PathVariable("id") Long badgeId,
            @Parameter(description = "更新徽章请求") @Valid @RequestBody UpdateBadgeRequest request) {
        BadgeVO vo = badgeService.updateBadge(badgeId, request);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除徽章", description = "管理后台删除徽章")
    public ApiResponse<Void> deleteBadge(
            @Parameter(description = "徽章ID", required = true) @PathVariable("id") Long badgeId) {
        badgeService.deleteBadge(badgeId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/grant")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "授予徽章", description = "管理后台向指定用户授予徽章")
    public ApiResponse<Void> grantBadge(
            @Parameter(description = "徽章ID", required = true) @PathVariable("id") Long badgeId,
            @Parameter(description = "用户ID信息") @RequestBody Map<String, Long> body) {
        badgeService.grantBadge(body.get("userId"), badgeId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "撤销徽章", description = "管理后台撤销指定用户的徽章")
    public ApiResponse<Void> revokeBadge(
            @Parameter(description = "徽章ID", required = true) @PathVariable("id") Long badgeId,
            @Parameter(description = "用户ID信息") @RequestBody Map<String, Long> body) {
        badgeService.revokeBadge(body.get("userId"), badgeId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "切换徽章状态", description = "管理后台切换徽章启用/禁用状态")
    public ApiResponse<Void> updateBadgeStatus(
            @Parameter(description = "徽章ID", required = true) @PathVariable("id") Long badgeId,
            @Parameter(description = "状态值") @RequestParam Integer status) {
        badgeService.updateBadgeStatus(badgeId, status);
        return ApiResponse.ok(null);
    }
}
