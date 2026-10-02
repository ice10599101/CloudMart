package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.OrderTodayStatsResponse;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import java.util.Map;
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
            public ApiResponse<Object> pageAfterSaleCases(long page, long pageSize, String status, Long orderId) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> approveAfterSaleCase(Long caseId, Map<String, Object> body) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> inspectAfterSaleCase(Long caseId, Map<String, Object> body) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> rejectAfterSaleCase(Long caseId, Map<String, Object> body) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listOrders(String status, Long userId,
                                                   String orderNo, int page, int size) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getOrderById(Long orderId) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> shipOrder(Long orderId, java.util.Map<String, Object> request) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> cancelOrder(Long orderId) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> approveRefund(Long orderId) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> rejectRefund(Long orderId, String rejectReason) {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<OrderTodayStatsResponse> getTodayStats() {
                throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
            }
        };
    }
}
