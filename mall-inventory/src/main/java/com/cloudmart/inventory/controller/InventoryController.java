package com.cloudmart.inventory.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.inventory.converter.InventoryConverter;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.dto.ReleaseRequest;
import com.cloudmart.inventory.service.InventoryService;
import com.cloudmart.inventory.vo.InventoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "库存管理", description = "库存查询、预扣、释放、确认接口")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryConverter inventoryConverter;

    public InventoryController(InventoryService inventoryService, InventoryConverter inventoryConverter) {
        this.inventoryService = inventoryService;
        this.inventoryConverter = inventoryConverter;
    }

    @GetMapping("/{skuId}")
    @Operation(summary = "查询库存", description = "根据SKU ID查询库存信息")
    public ApiResponse<InventoryVO> getInventory(
            @Parameter(description = "SKU ID") @PathVariable("skuId") Long skuId) {
        InventoryDTO dto = inventoryService.getInventory(skuId);
        return ApiResponse.ok(inventoryConverter.dtoToVO(dto));
    }

    @PostMapping("/deduct")
    @Operation(summary = "预扣库存", description = "下单时预扣库存，Redis Lua原子操作保证并发安全")
    public ApiResponse<Boolean> deductStock(@Valid @RequestBody DeductRequest request) {
        return ApiResponse.ok(inventoryService.deductStock(request));
    }

    @PostMapping("/release")
    @Operation(summary = "释放库存", description = "订单取消时释放预扣库存")
    public ApiResponse<Void> releaseStock(@Valid @RequestBody ReleaseRequest request) {
        inventoryService.releaseStock(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认扣减", description = "订单支付成功后确认扣减，将预占转为真实扣减")
    public ApiResponse<Void> confirmDeduct(
            @Parameter(description = "SKU ID") @RequestParam("skuId") @NotNull Long skuId,
            @Parameter(description = "数量") @RequestParam("quantity") @NotNull @Min(1) Integer quantity,
            @Parameter(description = "订单ID") @RequestParam(value = "orderId", required = false) Long orderId) {
        inventoryService.confirmDeduct(skuId, quantity, orderId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/init")
    @Operation(summary = "初始化库存", description = "初始化SKU库存到DB和Redis")
    public ApiResponse<Void> initStock(
            @Parameter(description = "SKU ID") @RequestParam("skuId") @NotNull Long skuId,
            @Parameter(description = "商品ID") @RequestParam("productId") @NotNull Long productId,
            @Parameter(description = "库存数量") @RequestParam("stock") @NotNull @Min(0) Integer stock) {
        inventoryService.initStock(skuId, productId, stock);
        return ApiResponse.ok(null);
    }
}
