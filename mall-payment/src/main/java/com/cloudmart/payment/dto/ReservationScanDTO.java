package com.cloudmart.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 预占台账对账扫描视图（T11）：mall-inventory 返回的订单级聚合。
 */
@Schema(description = "预占台账对账扫描视图")
public record ReservationScanDTO(
    Long orderId,
    String status,
    Integer quantity,
    LocalDateTime createdAt
) {}
