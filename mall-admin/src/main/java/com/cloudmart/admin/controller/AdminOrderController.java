package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.OrderTodayStatsResponse;
import com.cloudmart.admin.feign.OrderFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务管理-订单交易域：订单、售后、退款、当日统计。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：订单/售后/退款")
public class AdminOrderController {

    private final OrderFeignClient orderFeignClient;

    public AdminOrderController(OrderFeignClient orderFeignClient) {
        this.orderFeignClient = orderFeignClient;
    }

    // ==================== 订单 ====================

    @GetMapping("/orders")
    @RequiresPermission("business:order:list")
    @Operation(summary = "订单列表", description = "查询所有订单，支持按状态、订单号筛选")
    public ApiResponse<Object> listOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int size) {
        return orderFeignClient.listOrders(status, userId, orderNo, page, size);
    }

    @GetMapping("/orders/{orderId}")
    @RequiresPermission("business:order:query")
    @Operation(summary = "订单详情", description = "查询订单详情")
    public ApiResponse<Object> getOrder(@PathVariable Long orderId) {
        return orderFeignClient.getOrderById(orderId);
    }

    @PutMapping("/orders/{orderId}/ship")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:ship")
    @Operation(summary = "订单发货", description = "将已支付订单标记为已发货")
    public ApiResponse<Object> shipOrder(@PathVariable Long orderId,
            @RequestBody(required = false)
            Map<String, Object> request) {
        // WMS-01 闭环：透传承运商/运单号到订单服务（由其建立真实包裹并出库）
        return orderFeignClient.shipOrder(orderId, request == null ? Map.of() : request);
    }

    @PutMapping("/orders/{orderId}/cancel")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:cancel")
    @Operation(summary = "取消订单", description = "管理员取消订单")
    public ApiResponse<Object> cancelOrder(@PathVariable Long orderId) {
        return orderFeignClient.cancelOrder(orderId);
    }

    @GetMapping("/orders/after-sale")
    @RequiresPermission("business:order:refund")
    @Operation(summary = "售后案件分页", description = "T11：按状态/订单筛选售后案件")
    public ApiResponse<Object> listAfterSaleCases(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderId", required = false) Long orderId) {
        return orderFeignClient.pageAfterSaleCases(page, pageSize, status, orderId);
    }

    @PostMapping("/orders/after-sale/{caseId}/approve")
    @RequiresPermission("business:order:refund")
    @OperLog(title = "售后案件", businessType = 2)
    @Operation(summary = "受理售后", description = "T11：批准金额+关联退款单（T02），PENDING→APPROVED")
    public ApiResponse<Object> approveAfterSaleCase(@PathVariable Long caseId,
                                                    @RequestBody Map<String, Object> body) {
        return orderFeignClient.approveAfterSaleCase(caseId, body);
    }

    @PostMapping("/orders/after-sale/{caseId}/inspection")
    @RequiresPermission("business:order:refund")
    @OperLog(title = "售后案件", businessType = 2)
    @Operation(summary = "质检结果录入", description = "T11：人工质检 PASSED/REJECTED")
    public ApiResponse<Object> inspectAfterSaleCase(@PathVariable Long caseId,
                                                    @RequestBody Map<String, Object> body) {
        return orderFeignClient.inspectAfterSaleCase(caseId, body);
    }

    @PostMapping("/orders/after-sale/{caseId}/reject")
    @RequiresPermission("business:order:refund")
    @OperLog(title = "售后案件", businessType = 2)
    @Operation(summary = "拒绝售后", description = "T11：PENDING→REJECTED 留原因")
    public ApiResponse<Object> rejectAfterSaleCase(@PathVariable Long caseId,
                                                   @RequestBody Map<String, Object> body) {
        return orderFeignClient.rejectAfterSaleCase(caseId, body);
    }

    @PutMapping("/orders/{orderId}/refund/approve")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:refund")
    @Operation(summary = "审批通过退款", description = "管理员审批通过退款申请")
    public ApiResponse<Object> approveRefund(@PathVariable Long orderId) {
        return orderFeignClient.approveRefund(orderId);
    }

    @PutMapping("/orders/{orderId}/refund/reject")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:refund")
    @Operation(summary = "拒绝退款", description = "管理员拒绝退款申请")
    public ApiResponse<Object> rejectRefund(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        return orderFeignClient.rejectRefund(orderId, body.get("rejectReason"));
    }

    @GetMapping("/orders/stats/today")
    @RequiresPermission("business:order:list")
    @Operation(summary = "今日订单统计", description = "返回今日订单数量和营收总额")
    public ApiResponse<OrderTodayStatsResponse> getTodayOrderStats() {
        return orderFeignClient.getTodayStats();
    }
}
