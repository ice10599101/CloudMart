package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.OrderTodayStatsResponse;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.Map;
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
    ApiResponse<Object> shipOrder(@PathVariable("orderId") Long orderId,
                                  @RequestBody Map<String, Object> request);

    @PutMapping("/{orderId}/cancel")
    ApiResponse<Object> cancelOrder(@PathVariable("orderId") Long orderId);

    // T11：售后案件运营端
    @GetMapping("/after-sale")
    ApiResponse<Object> pageAfterSaleCases(@RequestParam("page") long page,
                                           @RequestParam("pageSize") long pageSize,
                                           @RequestParam(value = "status", required = false) String status,
                                           @RequestParam(value = "orderId", required = false) Long orderId);

    @PostMapping("/after-sale/{caseId}/approve")
    ApiResponse<Object> approveAfterSaleCase(@PathVariable("caseId") Long caseId,
                                             @RequestBody Map<String, Object> body);

    @PostMapping("/after-sale/{caseId}/inspection")
    ApiResponse<Object> inspectAfterSaleCase(@PathVariable("caseId") Long caseId,
                                             @RequestBody Map<String, Object> body);

    @PostMapping("/after-sale/{caseId}/reject")
    ApiResponse<Object> rejectAfterSaleCase(@PathVariable("caseId") Long caseId,
                                            @RequestBody Map<String, Object> body);

    @PutMapping("/{orderId}/approve-refund")
    ApiResponse<Object> approveRefund(@PathVariable("orderId") Long orderId);

    @PutMapping("/{orderId}/reject-refund")
    ApiResponse<Object> rejectRefund(@PathVariable("orderId") Long orderId,
                                      @RequestParam("rejectReason") String rejectReason);

    @GetMapping("/today-stats")
    ApiResponse<OrderTodayStatsResponse> getTodayStats();
}
