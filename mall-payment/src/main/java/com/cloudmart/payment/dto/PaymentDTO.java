package com.cloudmart.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
