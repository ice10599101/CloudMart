package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * T02：内部退款 Feign 客户端（order → payment）。
 * 仅订单服务持有 order:refund scope 服务令牌；终端不得直接退款。
 * 出站签名由 ServiceTokenFeignInterceptor 按 outbound-scopes.mall-payment=order:refund 附加。
 */
@FeignClient(contextId = "refundFeignClient", name = "mall-payment",
        fallbackFactory = RefundFeignClientFallbackFactory.class)
public interface RefundFeignClient {

    @PostMapping("/internal/refunds")
    ApiResponse<Map<String, Object>> createRefund(@RequestBody Map<String, Object> request);
}
