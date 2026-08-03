package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.InventoryDTO;
import com.cloudmart.admin.dto.feign.InventorySearchRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(contextId = "inventoryFeignClient", name = "mall-inventory", path = "/admin/inventory", fallbackFactory = InventoryFeignClientFallbackFactory.class)
public interface InventoryFeignClient {

    @GetMapping
    ApiResponse<List<InventoryDTO>> listInventory(@SpringQueryMap InventorySearchRequest request);

    @GetMapping("/{skuId}")
    ApiResponse<InventoryDTO> getInventory(@PathVariable("skuId") Long skuId);

    @PostMapping("/init")
    ApiResponse<Void> initStock(@RequestParam Long skuId, @RequestParam Long productId, @RequestParam Integer stock);
}
