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
    @Schema(description = "T05：已退累计金额") java.math.BigDecimal refundedAmount,
    @Schema(description = "T07：服务端计算的可用动作（PAY/CANCEL/CONFIRM_RECEIPT/REVIEW），"
            + "售后可用性由 /after-sale eligibility 独立判定") List<String> allowedActions
) {
}
