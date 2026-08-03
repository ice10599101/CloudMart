package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.DashboardStatsResponse;
import com.cloudmart.admin.dto.RecentOrderResponse;
import com.cloudmart.admin.dto.SalesTrendItem;
import com.cloudmart.admin.service.AdminDashboardService;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "控制台", description = "管理后台首页统计数据")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/stats")
    @Operation(summary = "获取控制台统计数据")
    public ApiResponse<DashboardStatsResponse> getStats() {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        return ApiResponse.ok(adminDashboardService.getStats());
    }

    @GetMapping("/recent-orders")
    @Operation(summary = "获取最近订单列表")
    public ApiResponse<List<RecentOrderResponse>> getRecentOrders(
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        return ApiResponse.ok(adminDashboardService.getRecentOrders(pageSize));
    }

    @GetMapping("/sales-trend")
    @Operation(summary = "获取销售趋势数据")
    public ApiResponse<List<SalesTrendItem>> getSalesTrend(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        return ApiResponse.ok(adminDashboardService.getSalesTrend(days));
    }
}
