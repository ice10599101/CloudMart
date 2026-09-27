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

/**
 * 库存 Feign 客户端（SEC-01）：目标迁移至 mall-inventory 的 /internal/inventory/**
 * （inventory:trade 服务令牌可达），旧根路径已随身份边界改造收敛。
 */
@FeignClient(name = "mall-inventory", contextId = "inventoryClient", path = "/internal/inventory", fallbackFactory = InventoryFeignClientFallbackFactory.class)
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

    @GetMapping("/skus/{skuId}")
    ApiResponse<InventoryDTO> getInventory(@PathVariable("skuId") Long skuId);

    record InventoryDTO(
            Long id, Long skuId, Long productId,
            Integer available, Integer reserved
    ) {}
}
