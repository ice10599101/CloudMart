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
    @Schema(description = "运费") BigDecimal freightAmount,
    @Schema(description = "优惠金额") BigDecimal discountAmount,
    @Schema(description = "订单项列表") List<OrderItemVO> items,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "支付时间") LocalDateTime paidAt
) {}
