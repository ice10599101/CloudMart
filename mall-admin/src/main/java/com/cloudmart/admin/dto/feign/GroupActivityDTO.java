package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GroupActivityDTO(
    Long id,
    String name,
    String description,
    Long productId,
    Long skuId,
    BigDecimal originalPrice,
    BigDecimal groupPrice,
    Integer targetNumber,
    Integer maxGroups,
    Integer currentGroups,
    Integer perUserLimit,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String status,
    LocalDateTime createdAt
) {}
