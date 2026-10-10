package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** N-5：徽标查询 fail-open（按未购）；资格查询 fail-closed（拒绝评价）。 */

/**
 * 评价资格查询降级（REVIEW-01）：资格数据读不到时必须拒绝评价——
 * "查不到已购证明"绝不能解释为"允许评价"（fail-closed）。
 */
@Slf4j
@Component
public class OrderPurchaseFeignClientFallbackFactory implements FallbackFactory<OrderPurchaseFeignClient> {

    @Override
    public OrderPurchaseFeignClient create(Throwable cause) {
        log.error("mall-order 评价资格查询失败: {}", cause.getMessage());
        final BusinessException unavailable =
                new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务暂不可用，无法校验评价资格");
        return new OrderPurchaseFeignClient() {
            @Override
            public ApiResponse<java.util.List<Long>> purchaseEligibility(Long userId, Long skuId) {
                log.error("mall-order 评价资格查询失败: {}", cause.getMessage());
                throw unavailable;
            }

            @Override
            public ApiResponse<Boolean> hasPurchasedProduct(Long userId, Long productId) {
                // N-5 fail-open：徽标查询失败按未购处理，不阻塞回答
                log.warn("N-5 已购标识查询失败（fail-open 按未购）: {}", cause.getMessage());
                return ApiResponse.ok(Boolean.FALSE);
            }
        };
    }
}
