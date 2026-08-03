package com.cloudmart.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "优惠券模板VO")
public record CouponTemplateVO(
    @Schema(description = "模板ID") Long id,
    @Schema(description = "优惠券名称") String name,
    @Schema(description = "类型") String type,
    @Schema(description = "优惠金额") BigDecimal discountValue,
    @Schema(description = "最低消费金额") BigDecimal minOrderAmount,
    @Schema(description = "总发行量") Integer totalCount,
    @Schema(description = "剩余库存") Integer remainingCount,
    @Schema(description = "每人限领数量") Integer perUserLimit,
    @Schema(description = "开始时间") LocalDateTime startTime,
    @Schema(description = "结束时间") LocalDateTime endTime,
    @Schema(description = "状态") String status
) {}
