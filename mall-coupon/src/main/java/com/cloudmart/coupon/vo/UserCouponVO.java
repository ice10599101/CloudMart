package com.cloudmart.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券VO。
 * 字段命名与实体/DTO 一致（templateId/templateName/discountAmount/thresholdAmount），
 * 消费方：用户前端（UserCoupon 类型）与 mall-order Feign（UserCouponDTO）均按此契约解析。
 */
@Schema(description = "用户优惠券VO")
public record UserCouponVO(
    @Schema(description = "用户券ID") Long id,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "优惠券模板ID") Long templateId,
    @Schema(description = "优惠券名称") String templateName,
    @Schema(description = "优惠券类型") String templateType,
    @Schema(description = "优惠金额") BigDecimal discountAmount,
    @Schema(description = "折扣率") BigDecimal discountRate,
    @Schema(description = "最低消费金额") BigDecimal thresholdAmount,
    @Schema(description = "状态") String status,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "领取时间") LocalDateTime receivedAt,
    @Schema(description = "使用时间") LocalDateTime usedAt,
    @Schema(description = "过期时间") LocalDateTime expiredAt
) {}
