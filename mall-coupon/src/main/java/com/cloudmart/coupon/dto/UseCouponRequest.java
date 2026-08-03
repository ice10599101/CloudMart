package com.cloudmart.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "使用优惠券请求")
public record UseCouponRequest(

    @Schema(description = "用户券ID")
    @NotNull Long userCouponId,

    @Schema(description = "订单ID")
    @NotNull Long orderId
) {}
