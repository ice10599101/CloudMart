package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单阻塞查询客户端（USER-01）：注销执行前核验用户是否有未结订单
 * （PENDING_PAYMENT/PAID/SHIPPED）。出站服务令牌按
 * {@code outbound-scopes[mall-order]=order:internal} 签名。
 */
@FeignClient(contextId = "userOrderBlockFeignClient", name = "mall-order", path = "/internal/orders",
        fallbackFactory = OrderBlockFeignClientFallbackFactory.class)
public interface OrderBlockFeignClient {

    @GetMapping("/has-open-orders")
    ApiResponse<Boolean> hasOpenOrders(@RequestParam("userId") Long userId,
                                       @RequestParam("token") String token);
}
