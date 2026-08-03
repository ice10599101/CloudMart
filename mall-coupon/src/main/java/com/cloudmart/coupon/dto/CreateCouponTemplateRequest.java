package com.cloudmart.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "创建优惠券模板请求")
public record CreateCouponTemplateRequest(

    @Schema(description = "优惠券名称", example = "新用户专享券")
    @NotBlank String name,

    @Schema(description = "类型: AMOUNT_OFF-满减, PERCENT_OFF-折扣", example = "AMOUNT_OFF")
    @NotBlank String type,

    @Schema(description = "使用门槛金额", example = "100.00")
    @NotNull @DecimalMin("0") BigDecimal thresholdAmount,

    @Schema(description = "优惠金额(满减券)", example = "20.00")
    @DecimalMin("0") BigDecimal discountAmount,

    @Schema(description = "折扣率(折扣券, 0.10~0.99)", example = "0.80")
    @DecimalMin("0.1") @DecimalMax("0.99") BigDecimal discountRate,

    @Schema(description = "总发行量", example = "1000")
    @NotNull @Min(1) Integer totalQuantity,

    @Schema(description = "每人限领数量", example = "1")
    @NotNull @Min(1) Integer perUserLimit,

    @Schema(description = "有效期类型: FIXED_DATE-固定时间段, FIXED_DAYS-领取后固定天数", example = "FIXED_DAYS")
    @NotBlank String validityType,

    @Schema(description = "固定有效期开始时间")
    LocalDateTime startTime,

    @Schema(description = "固定有效期结束时间")
    LocalDateTime endTime,

    @Schema(description = "领取后有效天数", example = "30")
    @Min(1) Integer validDays
) {}
