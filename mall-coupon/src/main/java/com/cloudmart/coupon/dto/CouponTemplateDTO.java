package com.cloudmart.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "优惠券模板DTO")
public record CouponTemplateDTO(

    @Schema(description = "模板ID")
    Long id,

    @Schema(description = "优惠券名称")
    String name,

    @Schema(description = "类型")
    String type,

    @Schema(description = "使用门槛金额")
    BigDecimal thresholdAmount,

    @Schema(description = "优惠金额")
    BigDecimal discountAmount,

    @Schema(description = "折扣率")
    BigDecimal discountRate,

    @Schema(description = "总发行量")
    Integer totalQuantity,

    @Schema(description = "剩余库存")
    Integer remainingQuantity,

    @Schema(description = "每人限领数量")
    Integer perUserLimit,

    @Schema(description = "有效期类型")
    String validityType,

    @Schema(description = "固定有效期开始时间")
    LocalDateTime startTime,

    @Schema(description = "固定有效期结束时间")
    LocalDateTime endTime,

    @Schema(description = "领取后有效天数")
    Integer validDays,

    @Schema(description = "状态")
    String status,

    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {}
