package com.cloudmart.payment.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderFeignClientFallbackFactory implements FallbackFactory<OrderFeignClient> {

    @Override
    public OrderFeignClient create(Throwable cause) {
        log.error("订单服务调用失败: {}", cause.getMessage());
        return new OrderFeignClient() {
            @Override
            public ApiResponse<Void> notifyPaymentSuccess(Long orderId) {
                log.error("通知订单支付成功降级, orderId={}: {}", orderId, cause.getMessage());
                return ApiResponse.ok(null);
            }

            @Override
            public ApiResponse<Void> notifyOrderCancel(Long orderId) {
                log.error("通知订单取消降级, orderId={}: {}", orderId, cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
