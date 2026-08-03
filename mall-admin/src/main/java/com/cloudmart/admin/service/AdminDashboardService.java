package com.cloudmart.admin.service;

import com.cloudmart.admin.dto.DashboardStatsResponse;
import com.cloudmart.admin.dto.RecentOrderResponse;
import com.cloudmart.admin.dto.SalesTrendItem;

import java.util.List;

public interface AdminDashboardService {
    DashboardStatsResponse getStats();
    List<RecentOrderResponse> getRecentOrders(int pageSize);
    List<SalesTrendItem> getSalesTrend(int days);
}
