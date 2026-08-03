package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付 Feign 传输对象，与 mall-payment 服务端 PaymentDTO 字段对齐
 */
public record PaymentDTO(
    Long id,
    Long orderId,
    String paymentNo,
    BigDecimal amount,
    String payMethod,
    String status,
    LocalDateTime paidAt,
    LocalDateTime createdAt,
    String payUrl
) {}
