package com.cloudmart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSkuRequest(
    @NotBlank String skuCode,
    String attributes,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    BigDecimal originalPrice,
    @NotNull @Min(0) Integer stock,
    String image
) {}
