package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "阶梯满减规则请求")
public record TieredRuleRequest(
    @Schema(description = "最低消费金额") @NotNull @DecimalMin("0.01") BigDecimal minAmount,
    @Schema(description = "优惠金额") @NotNull @DecimalMin("0.01") BigDecimal discountAmount
) {}
