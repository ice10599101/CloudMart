package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;

/**
 * 支付尝试后台传输对象（T01）：与 mall-payment /admin/payments 视图字段对齐。
 * 旧 PaymentDTO（payments 旧表）已随旧支付链路删除。
 */
public record PaymentAttemptDTO(
    Long attemptId,
    Long orderId,
    String merchantPaymentNo,
    String channel,
    String status,
    BigDecimal amount,
    String currency,
    String providerTxnNo,
    String expiresAt
) {}
