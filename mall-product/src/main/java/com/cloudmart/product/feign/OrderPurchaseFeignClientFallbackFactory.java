package com.cloudmart.product.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

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
        return (userId, skuId) -> {
            throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务暂不可用，无法校验评价资格");
        };
    }
}
