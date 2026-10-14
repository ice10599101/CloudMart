package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.product.dto.OrderItemEligibility;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * R08 订单行级评价资格：返回行权威事实（orderId/productId/skuId），不存在或未完成 data=null。
     */
    @GetMapping("/items/{orderItemId}/review-eligibility")
    ApiResponse<OrderItemEligibility> reviewEligibility(@RequestParam("userId") Long userId,
                                                        @PathVariable("orderItemId") Long orderItemId);

    /** N-5 问大家：已购标识（回答人"已购"徽标快照；fail-open 由 fallback 兜 false）。
     *  C36：path 已含 /internal/orders，方法路径不得重复前缀 */
    @GetMapping("/has-purchased-product")
    ApiResponse<Boolean> hasPurchasedProduct(@RequestParam("userId") Long userId,
                                             @RequestParam("productId") Long productId);
}
