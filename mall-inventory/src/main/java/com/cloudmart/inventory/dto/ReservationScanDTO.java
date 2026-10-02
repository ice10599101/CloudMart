package com.cloudmart.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 预占台账对账扫描视图（T11）：订单级聚合——每订单一行，
 * RESERVED 台账数量合计 + 最早创建时间，供跨服务对账核对。
 */
@Schema(description = "预占台账对账扫描视图")
public record ReservationScanDTO(
    Long orderId,
    /** 台账状态：RESERVED/CONFIRMED/RELEASED（同单多行时按优先级聚合） */
    String status,
    Integer quantity,
    LocalDateTime createdAt
) {}
