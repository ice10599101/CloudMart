package com.cloudmart.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
    @Min(1) Integer quantity,
    @Min(0) @Max(1) Integer checked
) {}
