package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.AdminAuditWishRequest;
import com.cloudmart.wish.dto.AdminWishListQuery;
import com.cloudmart.wish.service.AdminWishService;
import com.cloudmart.wish.vo.AdminWishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台心愿 Controller。
 *
 * <p>路由前缀 /admin/wishes，需管理员权限（网关层 X-Admin-Role 校验）。</p>
 */
@RestController
@RequestMapping("/admin/wishes")
@Tag(name = "管理后台-心愿管理", description = "心愿列表查看与审核操作")
@RequiredArgsConstructor
public class AdminWishController {

    private final AdminWishService adminWishService;

    @GetMapping
    @Operation(summary = "心愿列表（offset 分页）", description = "管理后台心愿列表，支持多维度筛选")
    public ApiResponse<List<AdminWishVO>> listWishes(@Valid AdminWishListQuery query) {
        Page<AdminWishVO> page = adminWishService.listWishes(query);
        return ApiResponse.ok(page.getRecords(), new ApiResponse.Meta(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal()
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "心愿详情", description = "管理后台心愿详情，含审核相关字段")
    public ApiResponse<AdminWishVO> getWishDetail(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId) {
        AdminWishVO vo = adminWishService.getWishDetail(wishId);
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{id}/audit")
    @Operation(summary = "审核心愿", description = "PENDING → APPROVED/REJECTED，已审核返回 409")
    @SentinelResource("WISH_AUDIT")
    public ApiResponse<AdminWishVO> auditWish(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "审核请求") @Valid @RequestBody AdminAuditWishRequest request) {
        AdminWishVO vo = adminWishService.auditWish(wishId, request);
        return ApiResponse.ok(vo);
    }
}
