package com.cloudmart.payment.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(contextId = "paymentOrderFeignClient", name = "mall-order", path = "/orders", fallbackFactory = OrderFeignClientFallbackFactory.class)
public interface OrderFeignClient {

    @PostMapping("/{orderId}/payment-success")
    ApiResponse<Void> notifyPaymentSuccess(@PathVariable("orderId") Long orderId);

    @PostMapping("/{orderId}/cancel-notify")
    ApiResponse<Void> notifyOrderCancel(@PathVariable("orderId") Long orderId);
}
