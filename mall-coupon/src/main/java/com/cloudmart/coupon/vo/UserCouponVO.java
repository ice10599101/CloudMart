package com.cloudmart.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户优惠券VO")
public record UserCouponVO(
    @Schema(description = "用户券ID") Long id,
    @Schema(description = "优惠券模板ID") Long couponTemplateId,
    @Schema(description = "优惠券名称") String couponName,
    @Schema(description = "优惠券类型") String couponType,
    @Schema(description = "优惠金额") BigDecimal discountValue,
    @Schema(description = "最低消费金额") BigDecimal minOrderAmount,
    @Schema(description = "状态") String status,
    @Schema(description = "使用时间") LocalDateTime usedAt,
    @Schema(description = "过期时间") LocalDateTime expiredAt
) {}
