package com.cloudmart.admin.dto.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 库存调整单 Feign 传输对象（TRADE-02），与 mall-inventory InventoryAdjustmentVO 字段对齐。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryAdjustmentDTO(
    String adjustNo,
    Long skuId,
    Long productId,
    Integer delta,
    String reason,
    String requestKey,
    Integer expectedVersion,
    Integer beforeAvailable,
    Integer afterAvailable,
    Integer beforeReserved,
    Long operatorId,
    LocalDateTime createdAt
) {}
