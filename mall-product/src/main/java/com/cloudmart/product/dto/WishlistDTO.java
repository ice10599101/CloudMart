package com.cloudmart.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WishlistDTO(
    Long id,
    Long productId,
    String productName,
    String mainImage,
    BigDecimal minPrice,
    String brand,
    LocalDateTime createdAt
) {}
