package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "物流订单DTO")
public record ShippingOrderDTO(
    @Schema(description = "物流订单ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "物流单号") String shippingNo,
    @Schema(description = "承运商") String carrier,
    @Schema(description = "状态: PENDING/SHIPPED/IN_TRANSIT/DELIVERED") String status,
    @Schema(description = "收件人姓名") String receiverName,
    @Schema(description = "收件人电话") String receiverPhone,
    @Schema(description = "收件人地址") String receiverAddress,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "更新时间") LocalDateTime updatedAt,
    @Schema(description = "物流轨迹") List<ShippingTrackingDTO> trackings
) {}
