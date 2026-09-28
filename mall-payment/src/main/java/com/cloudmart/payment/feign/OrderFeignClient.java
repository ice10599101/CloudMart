package com.cloudmart.payment.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 订单回调 Feign 客户端（SEC-01）：目标迁移至 mall-order 的
 * /internal/orders/**（服务令牌可达），旧 /orders/{id}/payment-success
 * 用户前缀路径已随身份边界改造移除。
 */
@FeignClient(contextId = "paymentOrderFeignClient", name = "mall-order", path = "/internal/orders", fallbackFactory = OrderFeignClientFallbackFactory.class)
public interface OrderFeignClient {

    @PostMapping("/payment-success/{orderId}")
    ApiResponse<Void> notifyPaymentSuccess(@PathVariable("orderId") Long orderId);

    @PostMapping("/cancel-notify/{orderId}")
    ApiResponse<Void> notifyOrderCancel(@PathVariable("orderId") Long orderId);

    /** SEC-04：读取订单权威归属，支付创建/查询前校验 requireOwner */
    @GetMapping("/{orderId}")
    ApiResponse<OrderInternalInfoDTO> getOrderInfo(@PathVariable("orderId") Long orderId);
}
