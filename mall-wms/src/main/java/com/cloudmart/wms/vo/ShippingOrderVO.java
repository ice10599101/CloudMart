package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "物流订单VO")
public record ShippingOrderVO(
    @Schema(description = "物流订单ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "物流单号") String trackingNo,
    @Schema(description = "承运商") String carrier,
    @Schema(description = "状态") String status,
    @Schema(description = "发货时间") LocalDateTime shippedAt
) {}
