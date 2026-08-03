package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板 Feign 传输对象，与 mall-coupon 服务端 CouponTemplateDTO 字段对齐
 */
public record CouponTemplateDTO(
    Long id,
    String name,
    String type,
    BigDecimal thresholdAmount,
    BigDecimal discountAmount,
    BigDecimal discountRate,
    Integer totalQuantity,
    Integer remainingQuantity,
    Integer perUserLimit,
    String validityType,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Integer validDays,
    String status,
    LocalDateTime createdAt
) {}
