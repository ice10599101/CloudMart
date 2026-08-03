package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 创建 SKU 请求，与 mall-product 服务端 CreateSkuRequest 字段对齐
 */
public record CreateSkuRequest(
    @NotBlank String skuCode,
    String attributes,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    BigDecimal originalPrice,
    @NotNull @Min(0) Integer stock,
    String image
) {}
