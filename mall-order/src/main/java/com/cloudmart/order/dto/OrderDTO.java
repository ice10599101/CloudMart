package com.cloudmart.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDTO(
    Long id,
    String orderNo,
    BigDecimal totalAmount,
    BigDecimal payAmount,
    BigDecimal discountAmount,
    Long couponId,
    String status,
    String receiverName,
    String receiverPhone,
    String receiverAddress,
    LocalDateTime shippedAt,
    LocalDateTime completedAt,
    String refundReason,
    String refundRejectReason,
    List<OrderItemDTO> items,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    /** T05：退款汇总状态（NONE/PARTIAL/FULL）——三端订单详情展示退款进度 */
    String refundStatus,
    /** T05：已退累计金额 */
    java.math.BigDecimal refundedAmount
) {
}
