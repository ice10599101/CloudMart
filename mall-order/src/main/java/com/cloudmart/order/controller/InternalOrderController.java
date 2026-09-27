package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
