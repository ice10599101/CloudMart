package com.cloudmart.order.dto;

import java.math.BigDecimal;

public record OrderItemDTO(
    Long id,
    Long productId,
    Long skuId,
    String productName,
    String skuImage,
    String skuAttributes,
    BigDecimal price,
    Integer quantity
) {}
