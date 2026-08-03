package com.cloudmart.inventory.dto;

import java.time.LocalDateTime;

public record InventoryLogDTO(
    Long id,
    Long skuId,
    String type,
    Integer quantity,
    Long orderId,
    LocalDateTime createdAt
) {}
