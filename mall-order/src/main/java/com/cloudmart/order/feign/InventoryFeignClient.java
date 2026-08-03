package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.InventoryDeductRequest;
import com.cloudmart.order.dto.InventoryReleaseRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "mall-inventory", contextId = "inventoryClient", fallbackFactory = InventoryFeignClientFallbackFactory.class)
public interface InventoryFeignClient {

    @PostMapping("/deduct")
    ApiResponse<Boolean> deductStock(@RequestBody InventoryDeductRequest request);

    @PostMapping("/release")
    ApiResponse<Void> releaseStock(@RequestBody InventoryReleaseRequest request);

    @PostMapping("/confirm")
    ApiResponse<Void> confirmDeduct(
            @RequestParam("skuId") @NotNull Long skuId,
            @RequestParam("quantity") @NotNull @Min(1) Integer quantity,
            @RequestParam(value = "orderId", required = false) Long orderId);

    @GetMapping("/{skuId}")
    ApiResponse<InventoryDTO> getInventory(@PathVariable("skuId") Long skuId);

    record InventoryDTO(
            Long id, Long skuId, Long productId,
            Integer available, Integer reserved
    ) {}
}
