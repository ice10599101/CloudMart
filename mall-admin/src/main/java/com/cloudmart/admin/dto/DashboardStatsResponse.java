package com.cloudmart.admin.dto;

import java.math.BigDecimal;

public record DashboardStatsResponse(
    long userCount,
    long roleCount,
    long menuCount,
    long onlineCount,
    long todayOrderCount,
    BigDecimal todayRevenue,
    long productCount,
    long memberCount
) {}
