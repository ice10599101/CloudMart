package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "物流轨迹DTO")
public record ShippingTrackingDTO(
    @Schema(description = "轨迹ID") Long id,
    @Schema(description = "物流订单ID") Long shippingOrderId,
    @Schema(description = "所在地") String location,
    @Schema(description = "描述") String description,
    @Schema(description = "发生时间") LocalDateTime happenedAt,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "更新时间") LocalDateTime updatedAt
) {}
