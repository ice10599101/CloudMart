package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 订单购买资格查询客户端（REVIEW-01）：评价前校验"该用户已完成订单包含该 SKU
 * 且传入 orderId 属于该列表"——订单归属/完成状态/SKU 匹配由 mall-order 权威判定。
 * 出站服务令牌按 {@code outbound-scopes[mall-order]=order:internal} 签名。
 */
@FeignClient(contextId = "productOrderFeignClient", name = "mall-order", path = "/internal/orders",
        fallbackFactory = OrderPurchaseFeignClientFallbackFactory.class)
public interface OrderPurchaseFeignClient {

    @GetMapping("/purchase-eligibility")
    ApiResponse<List<Long>> purchaseEligibility(@RequestParam("userId") Long userId,
                                                @RequestParam("skuId") Long skuId);
}
