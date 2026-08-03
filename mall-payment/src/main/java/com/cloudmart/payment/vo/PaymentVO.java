package com.cloudmart.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "支付VO")
public record PaymentVO(
    @Schema(description = "支付ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "支付单号") String paymentNo,
    @Schema(description = "金额") BigDecimal amount,
    @Schema(description = "支付方式") String payMethod,
    @Schema(description = "状态") String status,
    @Schema(description = "支付时间") LocalDateTime paidAt,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "支付链接") String payUrl
) {}
