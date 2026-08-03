package com.cloudmart.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券推荐结果 VO。
 *
 * @param couponIds      推荐使用的用户优惠券ID列表（按应用顺序）
 * @param originalAmount 订单原始金额
 * @param discountAmount 总优惠金额
 * @param finalAmount    最终支付金额
 */
@Schema(description = "优惠券推荐结果")
public record RecommendationVO(
    @Schema(description = "推荐使用的用户优惠券ID列表（按应用顺序）") List<Long> couponIds,
    @Schema(description = "订单原始金额") BigDecimal originalAmount,
    @Schema(description = "总优惠金额") BigDecimal discountAmount,
    @Schema(description = "最终支付金额") BigDecimal finalAmount
) {}
