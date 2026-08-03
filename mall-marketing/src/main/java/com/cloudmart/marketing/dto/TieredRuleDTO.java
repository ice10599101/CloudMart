package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "阶梯满减规则DTO")
public record TieredRuleDTO(
    @Schema(description = "规则ID") Long id,
    @Schema(description = "最低消费金额") BigDecimal minAmount,
    @Schema(description = "优惠金额") BigDecimal discountAmount
) {}
