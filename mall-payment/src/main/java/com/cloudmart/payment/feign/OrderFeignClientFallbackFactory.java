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

            @Override
            public ApiResponse<com.cloudmart.payment.dto.OrderInternalInfoDTO> getOrderInfo(Long orderId) {
                // SEC-04：归属数据读不到时返回空数据，调用方按订单不存在拒绝（fail-closed）
                log.error("查询订单归属信息降级, orderId={}: {}", orderId, cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
