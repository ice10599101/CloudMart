package com.cloudmart.product.dto;

import java.math.BigDecimal;

public record SkuDTO(
    Long id,
    Long productId,
    String skuCode,
    String attributes,
    BigDecimal price,
    BigDecimal originalPrice,
    Integer stock,
    String image,
    Integer status
) {}
