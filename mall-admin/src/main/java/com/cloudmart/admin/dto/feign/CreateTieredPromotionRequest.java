package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateTieredPromotionRequest(
    String name,
    String description,
    LocalDateTime startTime,
    LocalDateTime endTime,
    List<TieredRuleRequest> rules
) {
    public record TieredRuleRequest(
        BigDecimal minAmount,
        BigDecimal discountAmount
    ) {}
}
