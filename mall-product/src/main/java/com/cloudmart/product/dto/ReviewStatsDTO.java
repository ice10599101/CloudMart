package com.cloudmart.product.dto;

import java.math.BigDecimal;

public record ReviewStatsDTO(
    Long productId,
    BigDecimal averageRating,
    Integer totalReviews,
    Integer fiveStarCount,
    Integer fourStarCount,
    Integer threeStarCount,
    Integer twoStarCount,
    Integer oneStarCount
) {}
