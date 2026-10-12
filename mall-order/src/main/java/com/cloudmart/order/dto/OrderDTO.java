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
    java.math.BigDecimal refundedAmount,
    /** R11：是否需要走收费渠道支付——零元单（全额优惠）为 false，建单即内部结算完成 */
    Boolean paymentRequired
) {
    /** 兼容旧 17 参签名（退款汇总缺省 NONE/0；paymentRequired 按金额推导） */
    public OrderDTO(Long id, String orderNo, BigDecimal totalAmount, BigDecimal payAmount,
                    BigDecimal discountAmount, Long couponId, String status, String receiverName,
                    String receiverPhone, String receiverAddress, LocalDateTime shippedAt,
                    LocalDateTime completedAt, String refundReason, String refundRejectReason,
                    List<OrderItemDTO> items, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, orderNo, totalAmount, payAmount, discountAmount, couponId, status,
                receiverName, receiverPhone, receiverAddress, shippedAt, completedAt,
                refundReason, refundRejectReason, items, createdAt, updatedAt,
                "NONE", java.math.BigDecimal.ZERO,
                payAmount != null && payAmount.compareTo(java.math.BigDecimal.ZERO) > 0);
    }
}
