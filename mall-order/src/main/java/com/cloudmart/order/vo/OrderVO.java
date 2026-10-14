package com.cloudmart.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "订单VO")
public record OrderVO(
    @Schema(description = "订单ID") Long id,
    @Schema(description = "订单号") String orderNo,
    @Schema(description = "状态") String status,
    @Schema(description = "总金额") BigDecimal totalAmount,
    @Schema(description = "实付金额") BigDecimal payAmount,
    @Schema(description = "优惠金额") BigDecimal discountAmount,
    @Schema(description = "用户优惠券ID") Long couponId,
    @Schema(description = "收件人姓名") String receiverName,
    @Schema(description = "收件人电话") String receiverPhone,
    @Schema(description = "收件人地址") String receiverAddress,
    @Schema(description = "订单项列表") List<OrderItemVO> items,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "发货时间") LocalDateTime shippedAt,
    @Schema(description = "完成时间") LocalDateTime completedAt,
    @Schema(description = "T05：退款汇总状态（NONE/PARTIAL/FULL）") String refundStatus,
    @Schema(description = "T05：已退累计金额") java.math.BigDecimal refundedAmount
) {
    /** 兼容旧 14 参签名 */
    public OrderVO(Long id, String orderNo, String status, BigDecimal totalAmount,
                   BigDecimal payAmount, BigDecimal discountAmount, Long couponId,
                   String receiverName, String receiverPhone, String receiverAddress,
                   List<OrderItemVO> items, LocalDateTime createdAt, LocalDateTime shippedAt,
                   LocalDateTime completedAt) {
        this(id, orderNo, status, totalAmount, payAmount, discountAmount, couponId,
                receiverName, receiverPhone, receiverAddress, items, createdAt, shippedAt,
                completedAt, "NONE", java.math.BigDecimal.ZERO);
    }
}
