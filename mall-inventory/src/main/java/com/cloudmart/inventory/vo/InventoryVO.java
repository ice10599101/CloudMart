package com.cloudmart.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "库存VO")
public record InventoryVO(
    @Schema(description = "库存ID") Long id,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "可用库存") Integer availableStock,
    @Schema(description = "锁定库存") Integer lockedStock,
    @Schema(description = "更新时间") LocalDateTime updatedAt
) {}
