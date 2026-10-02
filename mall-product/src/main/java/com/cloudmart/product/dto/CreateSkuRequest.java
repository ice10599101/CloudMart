package com.cloudmart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSkuRequest(
    /** T08：SKU 身份稳定——更新已存在 SKU 时携带其 id（null=新增）；禁止重新分配已使用 ID */
    Long id,
    @NotBlank String skuCode,
    String attributes,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    BigDecimal originalPrice,
    @NotNull @Min(0) Integer stock,
    String image
) {}
