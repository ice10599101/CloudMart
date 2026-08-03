package com.cloudmart.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DeductRequest(
    @NotNull Long skuId,
    @NotNull @Min(1) Integer quantity,
    Long orderId
) {}
