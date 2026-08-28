package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.WarmEvent;
import com.cloudmart.wish.entity.WishFence;
import com.cloudmart.wish.dto.SaveFenceRequest;
import com.cloudmart.wish.service.WarmMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台围栏配置 + 温暖事件审核 Controller（Sprint 3.2 管理后台）。
 *
 * <p>路由前缀 /admin/warm-map，仅内部服务调用；权限点
 * {@code business:fence:list/add/edit}、{@code business:warmEvent:list/audit}。</p>
 */
@RestController
@RequestMapping("/admin/warm-map")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-围栏/温暖事件", description = "围栏 CRUD + 温暖事件审核（Sprint 3.2）")
@RequiredArgsConstructor
public class AdminWarmMapController {

    private final WarmMapService warmMapService;

    @GetMapping("/fences")
    @Operation(summary = "围栏列表", description = "全部围栏（含未启用；含中心坐标回显——仅管理端可见）")
    public ApiResponse<List<WishFence>> listFences(
            @Parameter(description = "心愿 ID 过滤") @RequestParam(required = false) Long wishId) {
        return ApiResponse.ok(warmMapService.listFences(wishId));
    }

    @PostMapping("/fences")
    @Operation(summary = "创建围栏", description = "半径最小 10m（半径 0 拒绝）；center 服务端 geohash7 编码存储")
    public ApiResponse<WishFence> createFence(
            @Valid @RequestBody SaveFenceRequest request,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(warmMapService.createFence(new WarmMapService.SaveFenceCommand(
                request.name(), request.wishId(), request.centerLat(), request.centerLng(),
                request.radiusM(), request.validFrom(), request.validTo(), request.isActive(),
                adminUserId)));
    }

    @PutMapping("/fences/{id}")
    @Operation(summary = "更新围栏", description = "字段覆盖式更新")
    public ApiResponse<WishFence> updateFence(
            @Parameter(description = "围栏 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody SaveFenceRequest request,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(warmMapService.updateFence(id, new WarmMapService.SaveFenceCommand(
                request.name(), request.wishId(), request.centerLat(), request.centerLng(),
                request.radiusM(), request.validFrom(), request.validTo(), request.isActive(),
                adminUserId)));
    }

    @PutMapping("/fences/{id}/active")
    @Operation(summary = "启用/停用围栏", description = "is_active=0 → 围栏判定恒 false")
    public ApiResponse<Void> toggleFence(
            @Parameter(description = "围栏 ID", required = true) @PathVariable Long id,
            @RequestParam boolean active) {
        warmMapService.toggleFence(id, active);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/fences/{id}")
    @Operation(summary = "删除围栏", description = "物理删除（配置数据非用户数据；到达记录保留审计）")
    public ApiResponse<Void> deleteFence(
            @Parameter(description = "围栏 ID", required = true) @PathVariable Long id) {
        warmMapService.deleteFence(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/warm-events")
    @Operation(summary = "温暖事件审核列表", description = "全状态分页（auditStatus 过滤可选）")
    public ApiResponse<List<WarmEvent>> listWarmEvents(
            @Parameter(description = "审核状态过滤") @RequestParam(required = false) String auditStatus,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.ok(warmMapService.listWarmEventsForAdmin(auditStatus, page, size));
    }

    @PutMapping("/warm-events/{id}/audit")
    @Operation(summary = "审核温暖事件", description = "APPROVED/REJECTED/AUTO_HIDDEN，同步 is_visible")
    public ApiResponse<WarmEvent> auditWarmEvent(
            @Parameter(description = "事件 ID", required = true) @PathVariable Long id,
            @RequestParam String auditStatus) {
        return ApiResponse.ok(warmMapService.auditWarmEvent(id, auditStatus));
    }
}
