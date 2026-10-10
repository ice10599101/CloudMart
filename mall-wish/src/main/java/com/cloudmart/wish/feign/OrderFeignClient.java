package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 心愿关联商品闭环（§6）：还愿购买凭证校验（mall-order 内部端点）。
 * 鉴权走 SEC-01 服务令牌（outbound-scopes 配置见 mall-wish yml；
 * mall-order 入站发行方白名单含 mall-wish）。
 */
@FeignClient(name = "mall-order", contextId = "wishOrderFeignClient",
        fallbackFactory = OrderFeignClientFallbackFactory.class)
public interface OrderFeignClient {

    @GetMapping("/internal/orders/purchase-evidence")
    ApiResponse<Boolean> purchaseEvidence(@RequestParam("userId") Long userId,
                                          @RequestParam("productId") Long productId,
                                          @RequestParam("orderId") Long orderId);
}
