package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;

/**
 * 今日订单统计 Feign 传输对象，与 mall-order 服务端 OrderTodayStatsResponse 字段对齐
 */
public record OrderTodayStatsResponse(
    long todayOrderCount,
    BigDecimal todayRevenue
) {}
