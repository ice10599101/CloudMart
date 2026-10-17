package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    // ===== OPS-03：账号注销（mall-user）/ 心愿数据导出（mall-wish）运维处置 =====

    @FeignClient(contextId = "userOperationsFeignClient", name = "mall-user", path = "/users/account-deletion",
            fallbackFactory = UserOperationsFallback.class)
    interface UserOperationsClient {
        @GetMapping("/internal/tasks")
        ApiResponse<Object> listTasks(@RequestParam("status") String status,
                                      @RequestParam("page") int page,
                                      @RequestParam("size") int size);

        @PostMapping("/internal/tasks/{taskId}/retry")
        ApiResponse<Object> retryTask(@PathVariable("taskId") long taskId,
                                      @RequestBody java.util.Map<String, String> body);
    }

    @FeignClient(contextId = "exportOperationsFeignClient", name = "mall-wish", path = "/admin/exports",
            fallbackFactory = ExportOperationsFallback.class)
    interface ExportOperationsClient {
        @GetMapping
        ApiResponse<Object> listTasks(@RequestParam("status") String status,
                                      @RequestParam("page") int page,
                                      @RequestParam("size") int size);

        @PostMapping("/{id}/retry")
        ApiResponse<Object> retryTask(@PathVariable("id") long id,
                                      @RequestBody java.util.Map<String, String> body);
    }

    @Component
    class UserOperationsFallback implements FallbackFactory<UserOperationsClient> {
        @Override
        public UserOperationsClient create(Throwable cause) {
            return new UserOperationsClient() {
                @Override
                public ApiResponse<Object> listTasks(String status, int page, int size) {
                    throw com.cloudmart.common.feign.FeignBusinessErrors.parse(cause,
                            "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
                }

                @Override
                public ApiResponse<Object> retryTask(long taskId, java.util.Map<String, String> body) {
                    throw com.cloudmart.common.feign.FeignBusinessErrors.parse(cause,
                            "USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试");
                }
            };
        }
    }

    @Component
    class ExportOperationsFallback implements FallbackFactory<ExportOperationsClient> {
        @Override
        public ExportOperationsClient create(Throwable cause) {
            return new ExportOperationsClient() {
                @Override
                public ApiResponse<Object> listTasks(String status, int page, int size) {
                    throw com.cloudmart.common.feign.FeignBusinessErrors.parse(cause,
                            "WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
                }

                @Override
                public ApiResponse<Object> retryTask(long id, java.util.Map<String, String> body) {
                    throw com.cloudmart.common.feign.FeignBusinessErrors.parse(cause,
                            "WISH_SERVICE_UNAVAILABLE", "心愿服务不可用，请稍后重试");
                }
            };
        }
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
