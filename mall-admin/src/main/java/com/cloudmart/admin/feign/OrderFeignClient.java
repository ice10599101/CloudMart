package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.OrderTodayStatsResponse;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "orderFeignClient", name = "mall-order", path = "/admin/orders", fallbackFactory = OrderFeignClientFallbackFactory.class)
public interface OrderFeignClient {

    @GetMapping
    ApiResponse<Object> listOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size);

    @GetMapping("/{orderId}")
    ApiResponse<Object> getOrderById(@PathVariable("orderId") Long orderId);

    @PutMapping("/{orderId}/ship")
    ApiResponse<Object> shipOrder(@PathVariable("orderId") Long orderId);

    @PutMapping("/{orderId}/cancel")
    ApiResponse<Object> cancelOrder(@PathVariable("orderId") Long orderId);

    @PutMapping("/{orderId}/approve-refund")
    ApiResponse<Object> approveRefund(@PathVariable("orderId") Long orderId);

    @PutMapping("/{orderId}/reject-refund")
    ApiResponse<Object> rejectRefund(@PathVariable("orderId") Long orderId,
                                      @RequestParam("rejectReason") String rejectReason);

    @GetMapping("/today-stats")
    ApiResponse<OrderTodayStatsResponse> getTodayStats();
}
