package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.InventoryDTO;
import com.cloudmart.admin.feign.InventoryFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务管理-库存域：SKU 库存查询与初始化。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：库存管理")
public class AdminInventoryController {

    private final InventoryFeignClient inventoryFeignClient;

    public AdminInventoryController(InventoryFeignClient inventoryFeignClient) {
        this.inventoryFeignClient = inventoryFeignClient;
    }

    // ==================== 库存 ====================

    @GetMapping("/inventory")
    @RequiresPermission("business:inventory:list")
    @Operation(summary = "库存列表", description = "分页查询库存列表，支持按商品ID筛选")
    public ApiResponse<List<InventoryDTO>> listInventory(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return inventoryFeignClient.listInventory(productId, page, pageSize);
    }

    @GetMapping("/inventory/{skuId}")
    @RequiresPermission("business:inventory:list")
    @Operation(summary = "查询库存", description = "根据SKU ID查询库存")
    public ApiResponse<InventoryDTO> getInventory(@PathVariable Long skuId) {
        return inventoryFeignClient.getInventory(skuId);
    }

    @PostMapping("/inventory/init")
    @OperLog(title = "库存管理", businessType = 1)
    @RequiresPermission("business:inventory:init")
    @Operation(summary = "初始化库存", description = "初始化SKU库存")
    public ApiResponse<Void> initInventory(@RequestBody Map<String, Object> body) {
        return inventoryFeignClient.initStock(
                ((Number) body.get("skuId")).longValue(),
                ((Number) body.get("productId")).longValue(),
                ((Number) body.get("stock")).intValue());
    }
}
