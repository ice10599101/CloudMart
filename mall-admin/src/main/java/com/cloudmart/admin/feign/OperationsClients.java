package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * T16 异常处理中心 Feign：汇总各域 outbox 脱敏视图与死信重试（各域执行自己的授权重试）。
 * 降级明确失败——运营需要真实错误，不假装受理。
 */
public interface OperationsClients {

    @FeignClient(contextId = "orderOperationsFeignClient", name = "mall-order", path = "/admin/operations",
            fallbackFactory = OrderOperationsFallback.class)
    interface OrderOperationsClient {
        @GetMapping("/outbox")
        ApiResponse<Object> listOutbox(@RequestParam("status") String status,
                                       @RequestParam("page") int page,
                                       @RequestParam("size") int size);

        @PostMapping("/outbox/{eventId}/retry")
        ApiResponse<Object> retryOutbox(@PathVariable("eventId") String eventId);
    }

    @FeignClient(contextId = "wishOperationsFeignClient", name = "mall-wish", path = "/admin/operations",
            fallbackFactory = WishOperationsFallback.class)
    interface WishOperationsClient {
        @GetMapping("/outbox")
        ApiResponse<Object> listOutbox(@RequestParam("status") String status,
                                       @RequestParam("page") int page,
                                       @RequestParam("size") int size);

        @PostMapping("/outbox/{eventId}/retry")
        ApiResponse<Object> retryOutbox(@PathVariable("eventId") String eventId);
    }

    @Component
    class OrderOperationsFallback implements FallbackFactory<OrderOperationsClient> {
        @Override
        public OrderOperationsClient create(Throwable cause) {
            return new OrderOperationsClient() {
                @Override
                public ApiResponse<Object> listOutbox(String status, int page, int size) {
                    throw FeignBusinessErrors.parse(cause, "ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，请稍后重试");
                }

                @Override
                public ApiResponse<Object> retryOutbox(String eventId) {
                    throw FeignBusinessErrors.parse(cause, "ORDER_SERVICE_UNAVAILABLE", "订单服务不可用，重试未受理");
                }
            };
        }
    }

    @Component
    class WishOperationsFallback implements FallbackFactory<WishOperationsClient> {
        @Override
        public WishOperationsClient create(Throwable cause) {
            return new WishOperationsClient() {
                @Override
                public ApiResponse<Object> listOutbox(String status, int page, int size) {
                    throw FeignBusinessErrors.parse(cause, "WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
                }

                @Override
                public ApiResponse<Object> retryOutbox(String eventId) {
                    throw FeignBusinessErrors.parse(cause, "WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，重试未受理");
                }
            };
        }
    }
}
