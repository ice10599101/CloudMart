package com.cloudmart.inventory.dto;

public record InventoryDTO(
    Long id,
    Long productId,
    Long skuId,
    Integer available,
    Integer reserved
) {}
