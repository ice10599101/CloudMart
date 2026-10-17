package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 物流订单 VO（TRADE-03：内部出库单号与承运商运单号分列，不混成一列）。
 */
@Schema(description = "物流订单VO")
public record ShippingOrderVO(
    @Schema(description = "物流订单ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "内部出库单号（SF 前缀，系统生成）") String shippingNo,
    @Schema(description = "承运商运单号") String trackingNo,
    @Schema(description = "承运商") String carrier,
    @Schema(description = "状态: PENDING/SHIPPED/IN_TRANSIT/DELIVERED") String status,
    @Schema(description = "发货时间") LocalDateTime shippedAt,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
