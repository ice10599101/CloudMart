package com.cloudmart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** T11：售后案件视图（含时间线）。 */
@Schema(description = "售后案件")
public record AfterSaleCaseVO(
    Long id,
    String caseNo,
    Long orderId,
    String orderNo,
    Long userId,
    Long itemId,
    String type,
    String reason,
    String attachmentFileIds,
    Integer quantity,
    String status,
    String refundNo,
    BigDecimal refundAmount,
    String rejectReason,
    LocalDateTime handledAt,
    LocalDateTime createdAt,
    java.util.List<TimelineEntry> timeline
) {
    @Schema(description = "时间线条目")
    public record TimelineEntry(String action, String operator, String detail, LocalDateTime createdAt) {}
}
