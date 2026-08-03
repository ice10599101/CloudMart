package com.cloudmart.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "用户优惠券DTO")
public record UserCouponDTO(

    @Schema(description = "用户券ID")
    Long id,

    @Schema(description = "用户ID")
    Long userId,

    @Schema(description = "优惠券模板ID")
    Long templateId,

    @Schema(description = "状态: UNUSED-未使用, USED-已使用, EXPIRED-已过期")
    String status,

    @Schema(description = "核销时关联的订单ID")
    Long orderId,

    @Schema(description = "领取时间")
    LocalDateTime receivedAt,

    @Schema(description = "使用时间")
    LocalDateTime usedAt,

    @Schema(description = "过期时间")
    LocalDateTime expiredAt,

    @Schema(description = "优惠券名称")
    String templateName,

    @Schema(description = "优惠券类型")
    String templateType,

    @Schema(description = "使用门槛金额")
    BigDecimal thresholdAmount,

    @Schema(description = "优惠金额")
    BigDecimal discountAmount,

    @Schema(description = "折扣率")
    BigDecimal discountRate
) {}
