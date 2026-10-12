package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * R12：购物篮满减自动匹配结果——多个活动重叠时返回当前最优（优惠金额最大）的一条，
 * 确定性次序：优惠金额 desc → 门槛 desc → promotionId asc。
 */
@Schema(description = "购物篮满减匹配结果")
public record TieredMatchResult(
    @Schema(description = "命中的活动 ID（未命中为 null）") Long promotionId,
    @Schema(description = "活动名称") String promotionName,
    @Schema(description = "命中的规则 ID（未命中为 null）") Long matchedRuleId,
    @Schema(description = "命中门槛金额") BigDecimal minAmount,
    @Schema(description = "满减优惠金额") BigDecimal discountAmount,
    @Schema(description = "是否命中") boolean matched
) {
    public static TieredMatchResult none() {
        return new TieredMatchResult(null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, false);
    }
}
