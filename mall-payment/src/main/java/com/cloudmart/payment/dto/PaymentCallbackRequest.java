package com.cloudmart.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCallbackRequest(
    @NotNull Long paymentId,
    @NotBlank String status,
    String transactionNo
) {}
