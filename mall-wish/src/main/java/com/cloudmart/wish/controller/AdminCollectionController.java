package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.Brand;
import com.cloudmart.wish.entity.VirtualAsset;
import com.cloudmart.wish.service.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台虚拟资产 + 品牌 Controller（Sprint 3.6 管理后台）。
 *
 * <p>路由前缀 /admin/collection 与 /admin/brand，仅内部服务调用；
 * 权限点 {@code business:asset:list,edit}、{@code business:brand:list,edit}。</p>
 */
@RestController
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-虚拟资产/品牌", description = "资产 CRUD/下架 + 品牌审核（Sprint 3.6）")
@RequiredArgsConstructor
public class AdminCollectionController {

    private final CollectionService collectionService;

    @GetMapping("/admin/collection/assets")
    @Operation(summary = "资产列表（全量含下架）")
    public ApiResponse<List<VirtualAsset>> listAssets() {
        return ApiResponse.ok(collectionService.listAllAssets());
    }

    @PostMapping("/admin/collection/assets")
    @Operation(summary = "创建/更新资产", description = "配置表化：新增皮肤仅插入配置行")
    public ApiResponse<VirtualAsset> saveAsset(@RequestBody VirtualAsset asset) {
        return ApiResponse.ok(collectionService.saveAsset(asset));
    }

    @PutMapping("/admin/collection/assets/{id}/active")
    @Operation(summary = "上/下架资产", description = "下架后用户不可再获取；已拥有的保留")
    public ApiResponse<Void> toggleAsset(
            @Parameter(description = "资产 ID", required = true) @PathVariable Long id,
            @RequestParam boolean active) {
        collectionService.toggleAsset(id, active);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/brand/list")
    @Operation(summary = "品牌列表（全状态）", description = "含 PENDING/REJECTED，入驻审核数据源")
    public ApiResponse<List<Brand>> listAllBrandsForAdmin() {
        return ApiResponse.ok(collectionService.listAllBrands());
    }

    @PostMapping("/admin/brand/{id}/audit")
    @Operation(summary = "品牌入驻审核", description = "APPROVED/REJECTED")
    public ApiResponse<Void> auditBrand(
            @Parameter(description = "品牌 ID", required = true) @PathVariable Long id,
            @RequestParam String status) {
        collectionService.auditBrand(id, status);
        return ApiResponse.ok(null);
    }
}
