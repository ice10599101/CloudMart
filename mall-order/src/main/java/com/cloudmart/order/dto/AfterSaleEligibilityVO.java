package com.cloudmart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * TRADE-01：售后可申请性视图——前端据此显示可选类型、剩余可退数量/金额与窗口，
 * 不再由客户端自行推断（allowedTypes 与服务端 apply 校验同源）。
 */
@Schema(description = "售后可申请性")
public record AfterSaleEligibilityVO(
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "订单项ID（null=整单维度）") Long itemId,
    @Schema(description = "是否可申请") boolean applicable,
    @Schema(description = "可申请类型枚举（依据：未发货仅退款；已发货/已完成按商品项，整单仅退款）")
    List<String> allowedTypes,
    @Schema(description = "剩余可申请数量（itemId 非空时为该行；整单维度为 null）")
    Integer remainingQuantity,
    @Schema(description = "剩余可退金额（itemId 非空时为该行剩余实付；整单维度为订单剩余实付）")
    BigDecimal remainingRefundAmount,
    @Schema(description = "申请截止时间（COMPLETED 订单=确认收货后 N 天；其他状态为 null）")
    LocalDateTime applyDeadlineAt,
    @Schema(description = "不可申请原因码（可申请为 null；与 apply 拒绝码同源）")
    String rejectReason
) {}
