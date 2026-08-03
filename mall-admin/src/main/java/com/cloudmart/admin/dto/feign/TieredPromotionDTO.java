package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TieredPromotionDTO(
    Long id,
    String name,
    String description,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String status,
    LocalDateTime createdAt,
    List<TieredRuleDTO> rules
) {
    public record TieredRuleDTO(
        Long id,
        BigDecimal minAmount,
        BigDecimal discountAmount
    ) {}
}
