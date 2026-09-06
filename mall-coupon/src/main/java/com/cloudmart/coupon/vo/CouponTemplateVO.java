package com.cloudmart.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板VO。
 * 字段命名与实体/DTO 一致（discountAmount/thresholdAmount/totalQuantity/remainingQuantity），
 * 消费方：用户前端、管理前端（admin/business/Coupons）均按此契约解析。
 */
@Schema(description = "优惠券模板VO")
public record CouponTemplateVO(
    @Schema(description = "模板ID") Long id,
    @Schema(description = "优惠券名称") String name,
    @Schema(description = "类型") String type,
    @Schema(description = "使用门槛金额") BigDecimal thresholdAmount,
    @Schema(description = "优惠金额") BigDecimal discountAmount,
    @Schema(description = "折扣率") BigDecimal discountRate,
    @Schema(description = "总发行量") Integer totalQuantity,
    @Schema(description = "剩余库存") Integer remainingQuantity,
    @Schema(description = "每人限领数量") Integer perUserLimit,
    @Schema(description = "有效期类型") String validityType,
    @Schema(description = "固定有效期开始时间") LocalDateTime startTime,
    @Schema(description = "固定有效期结束时间") LocalDateTime endTime,
    @Schema(description = "领取后有效天数") Integer validDays,
    @Schema(description = "状态") String status
) {}
