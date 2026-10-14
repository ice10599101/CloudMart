package com.cloudmart.order.mq;

/**
 * PAYMENT_SUCCEEDED 事件载荷（与 mall-payment PaymentAttemptService 出账 JSON 对齐）。
 *
 * @param orderId           订单 ID（必填）
 * @param paymentId         支付单 ID
 * @param merchantPaymentNo 支付权威流水号（R10：必填，迟到支付也要落到具体支付事实）
 * @param amount            渠道实付金额（BigDecimal 可解析字符串，与订单应付核对）
 * @param currency          币种
 */
public record PaymentSucceededPayload(
        Long orderId,
        Long paymentId,
        String merchantPaymentNo,
        String amount,
        String currency
) {
}
