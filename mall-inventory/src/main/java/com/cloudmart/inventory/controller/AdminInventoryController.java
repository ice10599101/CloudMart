package com.cloudmart.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.inventory.converter.InventoryConverter;
import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.service.InventoryService;
import com.cloudmart.inventory.vo.InventoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/inventory")
@Tag(name = "库存管理(后台)", description = "管理后台库存管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;
    private final InventoryConverter inventoryConverter;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "库存列表", description = "管理后台分页查询库存列表，支持按商品ID筛选")
    public ApiResponse<List<InventoryVO>> listInventory(
            @Parameter(description = "商品ID") @RequestParam(value = "productId", required = false) Long productId,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Page<InventoryDTO> result = inventoryService.listInventory(productId, page, pageSize);
        List<InventoryVO> voList = result.getRecords().stream().map(inventoryConverter::dtoToVO).toList();
        return ApiResponse.ok(voList, new Meta(page, pageSize, result.getTotal()));
    }

    @GetMapping("/{skuId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询库存", description = "管理后台根据SKU ID查询库存信息")
    public ApiResponse<InventoryVO> getInventory(
            @Parameter(description = "SKU ID") @PathVariable("skuId") Long skuId) {
        InventoryDTO dto = inventoryService.getInventory(skuId);
        return ApiResponse.ok(inventoryConverter.dtoToVO(dto));
    }

    @PostMapping("/init")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "初始化库存", description = "管理后台初始化SKU库存到DB和Redis")
    public ApiResponse<Void> initStock(
            @Parameter(description = "SKU ID") @RequestParam("skuId") @NotNull Long skuId,
            @Parameter(description = "商品ID") @RequestParam("productId") @NotNull Long productId,
            @Parameter(description = "库存数量") @RequestParam("stock") @NotNull @Min(0) Integer stock) {
        inventoryService.initStock(skuId, productId, stock);
        return ApiResponse.ok(null);
    }
}
