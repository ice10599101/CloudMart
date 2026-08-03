package com.cloudmart.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryDeductRequest(
        @NotNull Long skuId,
        @NotNull @Min(1) Integer quantity,
        Long orderId
) {}
