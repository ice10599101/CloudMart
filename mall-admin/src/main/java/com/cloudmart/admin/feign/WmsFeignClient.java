package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(contextId = "wmsFeignClient", name = "mall-wms", path = "/admin/wms", fallbackFactory = WmsFeignClientFallbackFactory.class)
public interface WmsFeignClient {

    @GetMapping("/pick-orders")
    ApiResponse<Object> listPickOrders(@RequestParam(value = "status", required = false) String status,
                                       @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                                       @RequestParam("page") Integer page,
                                       @RequestParam("size") Integer size);

    @GetMapping("/pick-orders/{id}")
    ApiResponse<Object> getPickOrder(@PathVariable("id") Long id);

    @PutMapping("/pick-orders/{id}/start")
    ApiResponse<Object> startPick(@PathVariable("id") Long id, @RequestParam Long assignedUserId);

    @PutMapping("/pick-orders/{id}/picked")
    ApiResponse<Object> confirmPicked(@PathVariable("id") Long id);

    @PutMapping("/pick-orders/{id}/packed")
    ApiResponse<Object> confirmPacked(@PathVariable("id") Long id);

    @GetMapping("/inbound-orders")
    ApiResponse<Object> listInboundOrders(@RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                                          @RequestParam("page") Integer page,
                                          @RequestParam("size") Integer size);

    @GetMapping("/inbound-orders/{id}")
    ApiResponse<Object> getInboundOrder(@PathVariable("id") Long id);

    @GetMapping("/warehouses")
    ApiResponse<Object> listWarehouses();

    @GetMapping("/shipping")
    ApiResponse<Object> listShipping(@RequestParam(value = "status", required = false) String status,
                                     @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                                     @RequestParam("page") Integer page,
                                     @RequestParam("size") Integer size);

    @PutMapping("/shipping/{id}/status")
    ApiResponse<Object> updateShippingStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PostMapping("/warehouses")
    ApiResponse<Object> createWarehouse(@RequestBody Map<String, Object> body);

    @PutMapping("/warehouses/{id}")
    ApiResponse<Object> updateWarehouse(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @DeleteMapping("/warehouses/{id}")
    ApiResponse<Void> deleteWarehouse(@PathVariable("id") Long id);
}
