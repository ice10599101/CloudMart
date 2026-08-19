package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.AdminCreateBadgeRequest;
import com.cloudmart.wish.dto.AdminUpdateBadgeRequest;
import com.cloudmart.wish.service.AdminBadgeService;
import com.cloudmart.wish.vo.AdminBadgeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台徽章 Controller（文档 33.4.7 徽章管理）。
 *
 * <p>路由前缀 /admin/badges，仅内部服务调用（mall-admin 经 Feign 代理转发，
 * hasRole('INTERNAL') 由 X-Internal-Call 头授予）。</p>
 */
@RestController
@RequestMapping("/admin/badges")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-徽章管理", description = "徽章新增/编辑/上下架 + condition JSON 编辑校验")
@RequiredArgsConstructor
public class AdminBadgeController {

    private final AdminBadgeService adminBadgeService;

    @GetMapping
    @Operation(summary = "徽章列表（全量含下架）", description = "badgeId 升序，含原始 condition JSON 供编辑器回显")
    public ApiResponse<List<AdminBadgeVO>> listBadges() {
        return ApiResponse.ok(adminBadgeService.listBadges());
    }

    @PostMapping
    @Operation(summary = "新增徽章", description = "code 唯一；condition 结构校验失败返回 BADGE_CONDITION_INVALID")
    public ApiResponse<AdminBadgeVO> createBadge(@Valid @RequestBody AdminCreateBadgeRequest request) {
        return ApiResponse.ok(adminBadgeService.createBadge(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑徽章", description = "code 不可修改；condition 结构校验同新增")
    public ApiResponse<AdminBadgeVO> updateBadge(
            @Parameter(description = "徽章 ID", required = true) @PathVariable("id") Long badgeId,
            @Valid @RequestBody AdminUpdateBadgeRequest request) {
        return ApiResponse.ok(adminBadgeService.updateBadge(badgeId, request));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "上/下架", description = "下架后不参与授予判定、不出现在徽章墙与图鉴；"
            + "已获得记录保留，重新上架自动恢复展示")
    public ApiResponse<AdminBadgeVO> updateBadgeStatus(
            @Parameter(description = "徽章 ID", required = true) @PathVariable("id") Long badgeId,
            @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        return ApiResponse.ok(adminBadgeService.updateBadgeStatus(badgeId,
                active == null || active));
    }
}
