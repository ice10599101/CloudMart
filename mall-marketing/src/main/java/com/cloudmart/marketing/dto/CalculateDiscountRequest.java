package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "满减计算请求")
public record CalculateDiscountRequest(
    @Schema(description = "满减活动ID") Long promotionId,
    @Schema(description = "订单金额") BigDecimal orderAmount
) {}
