package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.InventoryAdjustmentDTO;
import com.cloudmart.admin.dto.feign.InventoryDTO;
import com.cloudmart.admin.dto.feign.InventorySearchRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(contextId = "inventoryFeignClient", name = "mall-inventory", path = "/admin/inventory", fallbackFactory = InventoryFeignClientFallbackFactory.class)
public interface InventoryFeignClient {

    @GetMapping
    ApiResponse<List<InventoryDTO>> listInventory(@RequestParam(value = "productId", required = false) Long productId,
                                                  @RequestParam("page") Integer page,
                                                  @RequestParam("pageSize") Integer pageSize);

    @GetMapping("/{skuId}")
    ApiResponse<InventoryDTO> getInventory(@PathVariable("skuId") Long skuId);

    @PostMapping("/init")
    ApiResponse<Void> initStock(@RequestParam Long skuId, @RequestParam Long productId, @RequestParam Integer stock);

    // ---- TRADE-02：盘点调整单 ----

    @PostMapping("/adjustments")
    ApiResponse<InventoryAdjustmentDTO> adjustStock(
            @RequestBody com.cloudmart.admin.dto.InventoryAdjustRequest request,
            @RequestHeader("X-User-Id") Long operatorId);

    @GetMapping("/adjustments")
    ApiResponse<List<InventoryAdjustmentDTO>> listAdjustments(
            @RequestParam(value = "skuId", required = false) Long skuId,
            @RequestParam("page") Integer page,
            @RequestParam("pageSize") Integer pageSize);
}
