package com.cloudmart.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull Long orderId,
    @NotNull BigDecimal amount,
    String payMethod
) {}
