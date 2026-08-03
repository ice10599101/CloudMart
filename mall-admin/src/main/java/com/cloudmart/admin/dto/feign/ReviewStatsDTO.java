package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;

/**
 * 评价统计 Feign 传输对象，与 mall-product 服务端 ReviewStatsDTO 字段对齐
 */
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
