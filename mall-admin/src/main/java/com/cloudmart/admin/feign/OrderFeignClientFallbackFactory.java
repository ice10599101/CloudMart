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
        // 排障透传：下游 4xx 业务错误（如退款上限校验拒绝）必须保留原始错误码，
        // 不得吞成 ORDER_SERVICE_UNAVAILABLE 误导排障；5xx/连接类仍按服务不可用
        BusinessException passthrough = passthroughIfBusinessError(cause);
        BusinessException unavailable = new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
        BusinessException toThrow = passthrough != null ? passthrough : unavailable;
        BusinessException finalThrow = toThrow;
        return new OrderFeignClient() {
            @Override
            public ApiResponse<Object> pageAfterSaleCases(long page, long pageSize, String status, Long orderId) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> approveAfterSaleCase(Long caseId, Map<String, Object> body) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> inspectAfterSaleCase(Long caseId, Map<String, Object> body) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> rejectAfterSaleCase(Long caseId, Map<String, Object> body) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> listOrders(String status, Long userId,
                                                   String orderNo, int page, int size) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> getOrderById(Long orderId) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> shipOrder(Long orderId, java.util.Map<String, Object> request) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> cancelOrder(Long orderId) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> approveRefund(Long orderId) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<Object> rejectRefund(Long orderId, String rejectReason) {
                throw finalThrow;
            }

            @Override
            public ApiResponse<OrderTodayStatsResponse> getTodayStats() {
                throw finalThrow;
            }
        };
    }

    /**
     * FeignException 且 HTTP 4xx 时解析标准信封 error.code/error.message 透传；
     * 其余（连接失败/5xx/超时）返回 null——仍按服务不可用处理。
     */
    private static BusinessException passthroughIfBusinessError(Throwable cause) {
        if (!(cause instanceof feign.FeignException feignError)) {
            return null;
        }
        int status = feignError.status();
        if (status < 400 || status >= 500) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(feignError.contentUTF8());
            com.fasterxml.jackson.databind.JsonNode error = root.get("error");
            if (error != null && error.has("code")) {
                String code = error.get("code").asText();
                String message = error.has("message") ? error.get("message").asText() : "请求被下游服务拒绝";
                return new BusinessException(code, message);
            }
        } catch (Exception ignored) {
            // 非 JSON 响应体，按服务不可用处理
        }
        return null;
    }
}
