package com.cloudmart.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "阶梯满减规则VO")
public record TieredRuleVO(
    @Schema(description = "规则ID") Long id,
    @Schema(description = "最低金额") BigDecimal minAmount,
    @Schema(description = "优惠金额") BigDecimal discountAmount
) {}
