package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * P2-24 教训：凭证校验是"用户声称已购买"的防伪环节，fail-closed——
 * 订单服务不可用时返回 false（不回填凭证），用户可稍后重试。
 */
@Slf4j
@Component
public class OrderFeignClientFallbackFactory implements FallbackFactory<OrderFeignClient> {

    @Override
    public OrderFeignClient create(Throwable cause) {
        return (userId, productId, orderId) -> {
            log.warn("还愿购买凭证校验降级（fail-closed 拒绝回填）: userId={}, orderId={}", userId, orderId);
            return ApiResponse.fail("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
        };
    }
}
