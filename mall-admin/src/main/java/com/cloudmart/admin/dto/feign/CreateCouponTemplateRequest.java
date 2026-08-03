package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建优惠券模板请求，与 mall-coupon 服务端 CreateCouponTemplateRequest 字段对齐
 */
public record CreateCouponTemplateRequest(
    @NotBlank String name,
    @NotBlank String type,
    @NotNull @DecimalMin("0") BigDecimal thresholdAmount,
    @DecimalMin("0") BigDecimal discountAmount,
    @DecimalMin("0.1") @DecimalMax("0.99") BigDecimal discountRate,
    @NotNull @Min(1) Integer totalQuantity,
    @NotNull @Min(1) Integer perUserLimit,
    @NotBlank String validityType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    @Min(1) Integer validDays
) {}
