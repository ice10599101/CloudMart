package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.AdminAuditWishRequest;
import com.cloudmart.wish.dto.AdminWishListQuery;
import com.cloudmart.wish.dto.AdminWishTopRequest;
import com.cloudmart.wish.dto.AdminWishVisibilityRequest;
import com.cloudmart.wish.service.AdminWishService;
import com.cloudmart.wish.vo.AdminWishStatsVO;
import com.cloudmart.wish.vo.AdminWishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台心愿 Controller。
 *
 * <p>路由前缀 /admin/wishes，仅允许内部服务调用（mall-admin 经 Feign 代理转发），
 * 与 mall-community 管理端点安全模式一致：ROLE_INTERNAL 由网关注入的
 * X-Internal-Call 头经 InternalCallAuthenticationFilter 授予，外部请求无法伪造。</p>
 */
@RestController
@RequestMapping("/admin/wishes")
@PreAuthorize("hasRole('INTERNAL')")
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

    @GetMapping("/stats")
    @Operation(summary = "心愿宇宙综合统计", description = "管理工作台数据源：心愿总量/今日发布/"
            + "进行中/已实现/今日打卡/今日互动")
    public ApiResponse<AdminWishStatsVO> stats() {
        return ApiResponse.ok(adminWishService.stats());
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

    @PutMapping("/{id}/visibility")
    @Operation(summary = "上架/下架心愿", description = "对齐帖子管理模式：直接控制 is_visible，"
            + "下架后用户端不可见，管理端仍可查看")
    public ApiResponse<AdminWishVO> updateVisibility(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "上下架请求") @Valid @RequestBody AdminWishVisibilityRequest request) {
        AdminWishVO vo = adminWishService.updateVisibility(wishId, request.visible());
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{id}/top")
    @Operation(summary = "置顶/取消置顶", description = "对齐帖子管理模式：置顶心愿在用户端广场优先展示")
    public ApiResponse<AdminWishVO> updateTop(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId,
            @Parameter(description = "置顶请求") @Valid @RequestBody AdminWishTopRequest request) {
        AdminWishVO vo = adminWishService.updateTop(wishId, request.isTop());
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除心愿", description = "软删（deleted_at），对齐帖子管理模式")
    public ApiResponse<Void> deleteWish(
            @Parameter(description = "心愿 ID", required = true) @PathVariable("id") Long wishId) {
        adminWishService.deleteWish(wishId);
        return ApiResponse.ok(null);
    }
}
