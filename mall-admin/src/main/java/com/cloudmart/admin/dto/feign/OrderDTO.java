package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Feign 传输对象，与 mall-order 服务端 OrderDTO 字段对齐
 */
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
    LocalDateTime updatedAt
) {}
