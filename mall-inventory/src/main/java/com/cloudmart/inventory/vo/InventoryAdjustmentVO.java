package com.cloudmart.inventory.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "库存调整单 VO（TRADE-02）")
public record InventoryAdjustmentVO(
    @Schema(description = "调整单号") String adjustNo,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "调整量") Integer delta,
    @Schema(description = "调整原因") String reason,
    @Schema(description = "幂等键") String requestKey,
    @Schema(description = "提交时期望版本") Integer expectedVersion,
    @Schema(description = "调整前可用库存") Integer beforeAvailable,
    @Schema(description = "调整后可用库存") Integer afterAvailable,
    @Schema(description = "调整前预占库存") Integer beforeReserved,
    @Schema(description = "调整后预占库存") Integer afterReserved,
    @Schema(description = "操作管理员 ID") Long operatorId,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
