package com.cloudmart.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecentOrderResponse(
    Long id,
    String orderNo,
    String username,
    BigDecimal totalAmount,
    String status,
    LocalDateTime createdAt
) {}
