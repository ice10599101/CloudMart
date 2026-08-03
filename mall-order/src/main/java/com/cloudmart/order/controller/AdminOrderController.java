package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderTodayStatsResponse;
import com.cloudmart.order.service.OrderService;
import com.cloudmart.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
@Tag(name = "订单管理(后台)", description = "管理后台订单管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderConverter orderConverter;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "订单列表", description = "管理后台分页查询所有订单，支持按状态、用户ID、订单号筛选")
    public ApiResponse<List<OrderVO>> listAllOrders(
            @Parameter(description = "订单状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "用户ID") @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "订单号") @RequestParam(value = "orderNo", required = false) String orderNo,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "10") int size) {
        ApiResponse<List<OrderDTO>> response = orderService.listAllOrders(status, userId, orderNo, page, size);
        List<OrderVO> voList = orderConverter.orderDtoToVOList(response.data());
        return ApiResponse.ok(voList, response.meta());
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "订单详情", description = "管理后台查询订单详情")
    public ApiResponse<OrderVO> getOrderById(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.getAdminOrderById(orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PutMapping("/{orderId}/ship")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "订单发货", description = "管理后台将已支付订单标记为已发货")
    public ApiResponse<OrderVO> shipOrder(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.shipOrder(orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "取消订单", description = "管理后台取消订单")
    public ApiResponse<OrderVO> cancelOrder(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.adminCancelOrder(orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PutMapping("/{orderId}/approve-refund")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "同意退款", description = "管理后台同意退款申请")
    public ApiResponse<OrderVO> approveRefund(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.approveRefund(orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PutMapping("/{orderId}/reject-refund")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "拒绝退款", description = "管理后台拒绝退款申请")
    public ApiResponse<OrderVO> rejectRefund(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId,
            @Parameter(description = "拒绝原因", required = true) @RequestParam("rejectReason") String rejectReason) {
        OrderDTO dto = orderService.rejectRefund(orderId, rejectReason);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @GetMapping("/today-stats")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "今日订单统计", description = "管理后台查询今日订单统计数据")
    public ApiResponse<OrderTodayStatsResponse> getTodayStats() {
        return orderService.getTodayStats();
    }
}
