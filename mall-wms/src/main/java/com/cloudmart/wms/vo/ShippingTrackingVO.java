package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 物流轨迹（T05：三端订单详情展示“已发货/轨迹”）。 */
@Schema(description = "物流轨迹VO")
public record ShippingTrackingVO(
    @Schema(description = "轨迹ID") Long id,
    @Schema(description = "物流订单ID") Long shippingOrderId,
    @Schema(description = "地点") String location,
    @Schema(description = "描述") String description,
    @Schema(description = "发生时间") LocalDateTime happenedAt,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {
}
