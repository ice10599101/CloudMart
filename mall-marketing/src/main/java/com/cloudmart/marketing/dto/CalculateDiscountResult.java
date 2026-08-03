package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "满减计算结果")
public record CalculateDiscountResult(
    @Schema(description = "匹配的规则ID，无匹配时为null") Long matchedRuleId,
    @Schema(description = "最低消费金额") BigDecimal minAmount,
    @Schema(description = "优惠金额") BigDecimal discountAmount,
    @Schema(description = "是否匹配") boolean matched
) {}
