package com.cloudmart.order.dto;

import java.math.BigDecimal;

public record OrderTodayStatsResponse(
    long todayOrderCount,
    BigDecimal todayRevenue
) {}
