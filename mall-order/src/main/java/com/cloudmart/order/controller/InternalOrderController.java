package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单内部回调接口（SEC-01）：供 mall-payment 经服务令牌回调，替代旧的
 * {@code /orders/{orderId}/payment-success|cancel-notify} 用户前缀路径——
 * 旧路径挂在 /orders 用户前缀下，任何登录用户都曾可触达支付确认。
 *
 * <p>访问控制链：mall-payment 持 order:internal 能力域的服务令牌
 * → ServiceTokenAuthenticationFilter 校验后授予 ROLE_INTERNAL
 * → 本控制器 @PreAuthorize 二次确认。</p>
 */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "内部-订单回调", description = "支付服务回调通知（服务令牌可达）")
public class InternalOrderController {

    private final OrderService orderService;

    @PostMapping("/payment-success/{orderId}")
    @Operation(summary = "支付成功通知", description = "支付服务回调通知订单支付成功")
    public ApiResponse<Void> notifyPaymentSuccess(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        orderService.notifyPaymentSuccess(orderId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/cancel-notify/{orderId}")
    @Operation(summary = "订单取消通知", description = "支付服务回调通知订单取消（退款）")
    public ApiResponse<Void> notifyOrderCancel(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        orderService.notifyOrderCancel(orderId);
        return ApiResponse.ok(null);
    }

    /**
     * SEC-04：订单最小内部信息——支付/履约服务在做对象归属校验（requireOwner）
     * 时读取权威归属，只暴露 orderId/userId/status。
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "订单内部信息", description = "归属校验用最小字段（服务令牌可达）")
    public ApiResponse<com.cloudmart.order.dto.OrderInternalInfoDTO> getInternalInfo(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(orderService.getInternalOrderInfo(orderId));
    }

    /** USER-01：注销阻塞核验——用户是否存在未结订单（PENDING_PAYMENT/PAID/SHIPPED） */
    @GetMapping("/has-open-orders")
    @Operation(summary = "未结订单核验", description = "注销编排前置：有未结订单返回 true（阻塞注销）")
    public ApiResponse<Boolean> hasOpenOrders(
            @Parameter(description = "用户 ID", required = true) @org.springframework.web.bind.annotation.RequestParam("userId") Long userId,
            @org.springframework.web.bind.annotation.RequestParam(value = "token", required = false) String token) {
        return ApiResponse.ok(orderService.hasOpenOrders(userId));
    }

    /** REVIEW-01：评价资格判定——该用户已完成订单中包含指定 SKU 的订单列表 */
    @GetMapping("/purchase-eligibility")
    @Operation(summary = "购买资格查询", description = "userId+skuId；返回已完成且包含该 SKU 的订单 ID")
    public ApiResponse<List<Long>> purchaseEligibility(
            @Parameter(description = "用户 ID", required = true) @org.springframework.web.bind.annotation.RequestParam("userId") Long userId,
            @Parameter(description = "SKU ID", required = true) @org.springframework.web.bind.annotation.RequestParam("skuId") Long skuId) {
        return ApiResponse.ok(orderService.findCompletedOrderIdsWithSku(userId, skuId));
    }
}
